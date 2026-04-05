package com.chainpay.core.blockchain.worker;

import com.chainpay.core.blockchain.domain.BlockchainTransaction;
import com.chainpay.core.blockchain.repository.BlockchainTransactionRepository;
import com.chainpay.core.ledger.domain.Asset;
import com.chainpay.core.payout.domain.Payout;
import com.chainpay.core.payout.domain.PayoutStatus;
import com.chainpay.core.payout.repository.PayoutRepository;
import com.chainpay.core.payout.service.PayoutStateMachine;
import com.chainpay.core.webhook.service.OutboxPublisherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlockchainWorker {

    private final PayoutRepository payoutRepository;
    private final BlockchainTransactionRepository blockchainTxRepository;
    private final PayoutStateMachine stateMachine;
    private final OutboxPublisherService outboxPublisher;
    private final Web3j web3j;

    @Value("${chainpay.web3.hot-wallet-private-key}")
    private String privateKey;

    @Value("${chainpay.web3.gateway-contract-address:0x2279B7A0a67DB372996a5FaB50D91eAA73d2eBe6}")
    private String gatewayContractAddress;

    private final AtomicLong nonceTracker = new AtomicLong(-1);

    @Scheduled(fixedDelay = 5000) // Poll every 5 seconds
    // NOTE: No @Transactional here — each inner method owns its own REQUIRES_NEW transaction.
    // Combining @Transactional with synchronized on inner methods (via Spring proxy) can cause
    // deadlocks where the proxy lock is held across transaction boundaries.
    public void processProcessingPayouts() {
        List<Payout> pendingPayouts = payoutRepository.findByStatus(PayoutStatus.PENDING);

        // Collect IDs of payouts routed to the batch path so we can skip them in single-payout loop.
        // Using a Set<UUID> instead of List.removeAll() to avoid reliance on equals()/hashCode()
        // across different JPA proxy instances (removeAll would silently no-op on proxy identity mismatch).
        Set<UUID> batchedPayoutIds = new HashSet<>();

        if (pendingPayouts.size() > 1) {
            List<Payout> ethBatch = pendingPayouts.stream()
                    .filter(p -> p.getAsset() == null || p.getAsset().getContractAddress() == null
                            || p.getAsset().getContractAddress().isBlank()
                            || "0x0000000000000000000000000000000000000000".equals(p.getAsset().getContractAddress()))
                    .toList();

            if (ethBatch.size() > 1) {
                processNativeBatchPayouts(ethBatch);
                ethBatch.forEach(p -> batchedPayoutIds.add(p.getId()));
            }
        }

        for (Payout payout : pendingPayouts) {
            // Skip payouts already routed to the batch path, and guard against status
            // race (payout may have been transitioned to PROCESSING by the batch method).
            if (batchedPayoutIds.contains(payout.getId())) continue;
            if (payout.getStatus() != PayoutStatus.PENDING) continue;
            processSinglePayout(payout);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public synchronized void processNativeBatchPayouts(List<Payout> batchList) {
        if (batchList == null || batchList.isEmpty()) return;

        try {
            log.info("BLOCKCHAIN BATCH WORKER: Aggregating {} PENDING Native ETH payouts into a single Smart Contract Batch Transaction!", batchList.size());
            UUID batchId = UUID.randomUUID();

            for (Payout payout : batchList) {
                stateMachine.transition(payout, PayoutStatus.PROCESSING, "Picked up by Batch BlockchainWorker", "BLOCKCHAIN_BATCH_WORKER");
            }

            Credentials credentials = Credentials.create(privateKey);
            String fromAddress = credentials.getAddress();

            BigInteger rpcNonce = web3j.ethGetTransactionCount(fromAddress, DefaultBlockParameterName.PENDING)
                    .send().getTransactionCount();
            long dbMaxNonce = blockchainTxRepository.findMaxNonceByFromAddress(fromAddress).orElse(-1L);
            long highestKnown = Math.max(rpcNonce.longValue(), dbMaxNonce + 1);
            long nextNonce = Math.max(highestKnown, nonceTracker.get() + 1);
            nonceTracker.set(nextNonce);
            BigInteger nonce = BigInteger.valueOf(nextNonce);

            BigInteger gasPrice = fetchLiveGasPrice();
            BigInteger totalValue = BigInteger.ZERO;

            List<Bytes32> payoutIdList = new ArrayList<>();
            List<Address> recipientList = new ArrayList<>();
            List<Uint256> amountList = new ArrayList<>();
            List<Utf8String> memoList = new ArrayList<>();

            for (Payout payout : batchList) {
                BigInteger val = payout.getAmount() != null ? payout.getAmount() : BigInteger.ONE;
                totalValue = totalValue.add(val);

                byte[] payoutIdBytes = new byte[32];
                byte[] uuidBytes = payout.getId().toString().replace("-", "").getBytes(StandardCharsets.UTF_8);
                System.arraycopy(uuidBytes, 0, payoutIdBytes, 0, Math.min(uuidBytes.length, 32));

                payoutIdList.add(new Bytes32(payoutIdBytes));
                recipientList.add(new Address(payout.getDestinationAddress()));
                amountList.add(new Uint256(val));
                memoList.add(new Utf8String("CHAINPAY:" + payout.getId()));
            }

            byte[] batchIdBytes = new byte[32];
            byte[] batchUuidBytes = batchId.toString().replace("-", "").getBytes(StandardCharsets.UTF_8);
            System.arraycopy(batchUuidBytes, 0, batchIdBytes, 0, Math.min(batchUuidBytes.length, 32));

            Function batchFunction = new Function(
                    "dispatchBatchPayout",
                    Arrays.asList(
                            new Bytes32(batchIdBytes),
                            new DynamicArray<>(Bytes32.class, payoutIdList),
                            new DynamicArray<>(Address.class, recipientList),
                            new DynamicArray<>(Uint256.class, amountList),
                            new DynamicArray<>(Utf8String.class, memoList)
                    ),
                    Collections.emptyList()
            );

            String encodedBatchFunction = FunctionEncoder.encode(batchFunction);
            BigInteger contractGasLimit = BigInteger.valueOf(100000L + (30000L * batchList.size()));

            RawTransaction rawTx = RawTransaction.createTransaction(
                    nonce, gasPrice, contractGasLimit, gatewayContractAddress, totalValue, encodedBatchFunction
            );

            byte[] signedMessage = TransactionEncoder.signMessage(rawTx, credentials);
            String hexValue = Numeric.toHexString(signedMessage);

            EthSendTransaction response = web3j.ethSendRawTransaction(hexValue).send();
            if (response.hasError()) {
                log.error("Web3j Batch RPC ERROR: {}", response.getError().getMessage());
                throw new RuntimeException("Web3j Batch RPC error: " + response.getError().getMessage());
            }

            String txHash = response.getTransactionHash();
            log.info("[BATCH-WORKER] BATCH DISPATCH SUCCESSFUL — {} payouts broadcasted in single EVM tx. Hash: {} -> Gateway: {}",
                    batchList.size(), txHash, gatewayContractAddress);

            for (Payout payout : batchList) {
                String memoStr = "CHAINPAY:" + payout.getId();
                // Gas cost is an estimate at submission time; actual gas is updated from receipt in BlockchainEventListener.
                BigInteger estimatedGasUsed = BigInteger.valueOf(50000L);
                BigInteger totalGasCostWei = gasPrice.multiply(estimatedGasUsed);
                String costInEth = new BigDecimal(totalGasCostWei)
                        .divide(new BigDecimal("1000000000000000000")).toPlainString() + " ETH";

                BlockchainTransaction tx = BlockchainTransaction.builder()
                        .payout(payout)
                        .txHash(txHash)
                        .fromAddress(fromAddress)
                        .toAddress(payout.getDestinationAddress())
                        .nonce(nonce.longValue())
                        .gasPrice(gasPrice)
                        .gasLimit(contractGasLimit)
                        .gasUsed(estimatedGasUsed)
                        .txCostEth(costInEth)
                        .onChainMemo(memoStr)
                        // Store original calldata + value for safe gas-bump re-broadcast reconstruction
                        .calldata(encodedBatchFunction)
                        .valueSentWei(payout.getAmount() != null ? payout.getAmount() : BigInteger.ONE)
                        .status("SUBMITTED")
                        .build();

                blockchainTxRepository.save(tx);
                stateMachine.transition(payout, PayoutStatus.SUBMITTED, "Broadcasted in batch transaction: " + txHash, "BLOCKCHAIN_BATCH_WORKER");
                outboxPublisher.publishEvent("PAYOUT", payout.getId().toString(), "PAYOUT_SUBMITTED",
                        String.format("{\"payoutId\":\"%s\",\"txHash\":\"%s\",\"batchId\":\"%s\"}", payout.getId(), txHash, batchId));
                payoutRepository.save(payout);
            }
        } catch (Exception ex) {
            log.error("Failed to process batch payouts: {}", ex.getMessage(), ex);
            for (Payout payout : batchList) {
                payout.setErrorReason(ex.getMessage());
                stateMachine.transition(payout, PayoutStatus.FAILED, "Batch RPC Exception: " + ex.getMessage(), "BLOCKCHAIN_BATCH_WORKER");
                payoutRepository.save(payout);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public synchronized void processSinglePayout(Payout payout) {
        try {
            log.info("Worker picking up PENDING payout ID {}", payout.getId());
            stateMachine.transition(payout, PayoutStatus.PROCESSING, "Picked up by BlockchainWorker", "BLOCKCHAIN_WORKER");

            Credentials credentials = Credentials.create(privateKey);
            String fromAddress = credentials.getAddress();
            log.info("Hot Wallet initialized for sender address: {}", fromAddress);

            BigInteger rpcNonce = web3j.ethGetTransactionCount(fromAddress, DefaultBlockParameterName.PENDING)
                    .send().getTransactionCount();

            long dbMaxNonce = blockchainTxRepository.findMaxNonceByFromAddress(fromAddress).orElse(-1L);
            long highestKnown = Math.max(rpcNonce.longValue(), dbMaxNonce + 1);
            long nextNonce = Math.max(highestKnown, nonceTracker.get() + 1);
            nonceTracker.set(nextNonce);
            BigInteger nonce = BigInteger.valueOf(nextNonce);
            log.info("Calculated bulletproof EVM Nonce #{} (RPC: {}, DB Max+1: {}) for sender address {}",
                    nonce, rpcNonce, dbMaxNonce + 1, fromAddress);

            BigInteger gasPrice = fetchLiveGasPrice();
            BigInteger value = payout.getAmount() != null ? payout.getAmount() : BigInteger.ONE;

            String txHash;
            RawTransaction rawTx;
            String encodedCalldata;
            BigInteger valueSent;
            Asset asset = payout.getAsset();

            if (asset != null && asset.getContractAddress() != null
                    && !asset.getContractAddress().isBlank()
                    && !asset.getContractAddress().equals("0x0000000000000000000000000000000000000000")) {

                Function function = new Function(
                        "transfer",
                        Arrays.asList(
                                new Address(payout.getDestinationAddress()),
                                new Uint256(value)
                        ),
                        Collections.emptyList()
                );
                encodedCalldata = FunctionEncoder.encode(function);
                valueSent = BigInteger.ZERO;
                BigInteger contractGasLimit = BigInteger.valueOf(65000L);

                rawTx = RawTransaction.createTransaction(
                        nonce, gasPrice, contractGasLimit, asset.getContractAddress(), valueSent, encodedCalldata
                );
                log.info("[WORKER] ERC-20 transfer — asset: {} contract: {} calldata: {}",
                        asset.getSymbol(), asset.getContractAddress(), encodedCalldata);
            } else {
                // Route through ChainPayGateway smart contract
                byte[] payoutIdBytes = new byte[32];
                byte[] uuidBytes = payout.getId().toString().replace("-", "").getBytes(StandardCharsets.UTF_8);
                System.arraycopy(uuidBytes, 0, payoutIdBytes, 0, Math.min(uuidBytes.length, 32));

                Function gatewayFunction = new Function(
                        "dispatchNativePayout",
                        Arrays.asList(
                                new Bytes32(payoutIdBytes),
                                new Address(payout.getDestinationAddress()),
                                new Utf8String("CHAINPAY:" + payout.getId())
                        ),
                        Collections.emptyList()
                );
                encodedCalldata = FunctionEncoder.encode(gatewayFunction);
                valueSent = value;
                BigInteger contractGasLimit = BigInteger.valueOf(100000L);

                rawTx = RawTransaction.createTransaction(
                        nonce, gasPrice, contractGasLimit, gatewayContractAddress, valueSent, encodedCalldata
                );
                log.info("[WORKER] Routed payout {} via ChainPayGateway ({}) — Function: dispatchNativePayout, Calldata: {}",
                        payout.getId(), gatewayContractAddress, encodedCalldata);
            }

            byte[] signedMessage = TransactionEncoder.signMessage(rawTx, credentials);
            String hexValue = Numeric.toHexString(signedMessage);

            EthSendTransaction response = web3j.ethSendRawTransaction(hexValue).send();
            if (response.hasError()) {
                log.error("Web3j RPC ethSendRawTransaction ERROR: {}", response.getError().getMessage());
                throw new RuntimeException("Web3j RPC error: " + response.getError().getMessage());
            }

            txHash = response.getTransactionHash();
            log.info("[WORKER] Broadcasted raw tx to ChainPay Gateway on Anvil EVM node. Tx Hash: {}", txHash);

            String memoStr = "CHAINPAY:" + payout.getId();
            // Gas limit used for submission; actual gas consumed is updated from receipt in BlockchainEventListener.
            BigInteger submissionGasLimit = rawTx.getGasLimit();

            BigInteger totalGasCostWei = gasPrice.multiply(submissionGasLimit);
            String costInEth = new BigDecimal(totalGasCostWei)
                    .divide(new BigDecimal("1000000000000000000")).toPlainString() + " ETH";

            BlockchainTransaction tx = BlockchainTransaction.builder()
                    .payout(payout)
                    .txHash(txHash)
                    .fromAddress(fromAddress)
                    .toAddress(payout.getDestinationAddress())
                    .nonce(nonce.longValue())
                    .gasPrice(gasPrice)
                    .gasLimit(submissionGasLimit)
                    .gasUsed(submissionGasLimit) // placeholder; overwritten by actual receipt gas in BlockchainEventListener
                    .txCostEth(costInEth)
                    .onChainMemo(memoStr)
                    // Store original calldata + value for safe gas-bump re-broadcast reconstruction (P0 fix)
                    .calldata(encodedCalldata)
                    .valueSentWei(valueSent)
                    .status("SUBMITTED")
                    .build();

            blockchainTxRepository.save(tx);

            stateMachine.transition(payout, PayoutStatus.SUBMITTED, "Broadcasted transaction: " + txHash, "BLOCKCHAIN_WORKER");
            outboxPublisher.publishEvent("PAYOUT", payout.getId().toString(), "PAYOUT_SUBMITTED",
                    String.format("{\"payoutId\":\"%s\",\"txHash\":\"%s\"}", payout.getId(), txHash));

            payoutRepository.save(payout);
        } catch (Exception ex) {
            log.error("Failed to process payout ID {}: {}", payout.getId(), ex.getMessage(), ex);
            payout.setErrorReason(ex.getMessage());
            stateMachine.transition(payout, PayoutStatus.FAILED, "RPC Exception: " + ex.getMessage(), "BLOCKCHAIN_WORKER");
            payoutRepository.save(payout);
        }
    }

    private BigInteger fetchLiveGasPrice() {
        try {
            org.web3j.protocol.core.methods.response.EthGasPrice ethGasPrice = web3j.ethGasPrice().send();
            if (!ethGasPrice.hasError() && ethGasPrice.getGasPrice() != null) {
                return ethGasPrice.getGasPrice();
            }
        } catch (Exception ex) {
            log.warn("[WORKER] ethGasPrice RPC query failed: {}. Using 20 Gwei baseline.", ex.getMessage());
        }
        return BigInteger.valueOf(20_000_000_000L); // 20 Gwei baseline
    }
}
