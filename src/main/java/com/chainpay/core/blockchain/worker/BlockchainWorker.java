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
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthSendTransaction;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
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
    @Transactional
    public void processProcessingPayouts() {
        List<Payout> pendingPayouts = payoutRepository.findByStatus(PayoutStatus.PENDING);
        if (pendingPayouts.size() > 1) {
            List<Payout> ethBatch = pendingPayouts.stream()
                    .filter(p -> p.getAsset() == null || p.getAsset().getContractAddress() == null
                            || p.getAsset().getContractAddress().isBlank()
                            || "0x0000000000000000000000000000000000000000".equals(p.getAsset().getContractAddress()))
                    .toList();

            if (ethBatch.size() > 1) {
                processNativeBatchPayouts(ethBatch);
                pendingPayouts.removeAll(ethBatch);
            }
        }

        for (Payout payout : pendingPayouts) {
            processSinglePayout(payout);
        }
    }

    @Transactional
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

            BigInteger rpcNonce = web3j.ethGetTransactionCount(fromAddress, org.web3j.protocol.core.DefaultBlockParameterName.PENDING)
                    .send().getTransactionCount();
            long dbMaxNonce = blockchainTxRepository.findMaxNonceByFromAddress(fromAddress).orElse(-1L);
            long highestKnown = Math.max(rpcNonce.longValue(), dbMaxNonce + 1);
            long nextNonce = Math.max(highestKnown, nonceTracker.get() + 1);
            nonceTracker.set(nextNonce);
            BigInteger nonce = BigInteger.valueOf(nextNonce);

            BigInteger gasPrice = BigInteger.valueOf(20000000000L);
            BigInteger totalValue = BigInteger.ZERO;

            List<org.web3j.abi.datatypes.generated.Bytes32> payoutIdList = new ArrayList<>();
            List<org.web3j.abi.datatypes.Address> recipientList = new ArrayList<>();
            List<org.web3j.abi.datatypes.generated.Uint256> amountList = new ArrayList<>();
            List<org.web3j.abi.datatypes.Utf8String> memoList = new ArrayList<>();

            for (Payout payout : batchList) {
                BigInteger val = payout.getAmount() != null ? payout.getAmount() : BigInteger.ONE;
                totalValue = totalValue.add(val);

                byte[] payoutIdBytes = new byte[32];
                byte[] uuidBytes = payout.getId().toString().replace("-", "").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                System.arraycopy(uuidBytes, 0, payoutIdBytes, 0, Math.min(uuidBytes.length, 32));

                payoutIdList.add(new org.web3j.abi.datatypes.generated.Bytes32(payoutIdBytes));
                recipientList.add(new org.web3j.abi.datatypes.Address(payout.getDestinationAddress()));
                amountList.add(new org.web3j.abi.datatypes.generated.Uint256(val));
                memoList.add(new org.web3j.abi.datatypes.Utf8String("CHAINPAY:" + payout.getId()));
            }

            byte[] batchIdBytes = new byte[32];
            byte[] batchUuidBytes = batchId.toString().replace("-", "").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            System.arraycopy(batchUuidBytes, 0, batchIdBytes, 0, Math.min(batchUuidBytes.length, 32));

            org.web3j.abi.datatypes.Function batchFunction = new org.web3j.abi.datatypes.Function(
                    "dispatchBatchPayout",
                    java.util.Arrays.asList(
                            new org.web3j.abi.datatypes.generated.Bytes32(batchIdBytes),
                            new org.web3j.abi.datatypes.DynamicArray<>(org.web3j.abi.datatypes.generated.Bytes32.class, payoutIdList),
                            new org.web3j.abi.datatypes.DynamicArray<>(org.web3j.abi.datatypes.Address.class, recipientList),
                            new org.web3j.abi.datatypes.DynamicArray<>(org.web3j.abi.datatypes.generated.Uint256.class, amountList),
                            new org.web3j.abi.datatypes.DynamicArray<>(org.web3j.abi.datatypes.Utf8String.class, memoList)
                    ),
                    java.util.Collections.emptyList()
            );

            String encodedFunction = org.web3j.abi.FunctionEncoder.encode(batchFunction);
            BigInteger contractGasLimit = BigInteger.valueOf(100000L + (30000L * batchList.size()));

            org.web3j.crypto.RawTransaction rawTx = org.web3j.crypto.RawTransaction.createTransaction(
                    nonce, gasPrice, contractGasLimit, gatewayContractAddress, totalValue, encodedFunction
            );

            byte[] signedMessage = org.web3j.crypto.TransactionEncoder.signMessage(rawTx, credentials);
            String hexValue = org.web3j.utils.Numeric.toHexString(signedMessage);

            EthSendTransaction response = web3j.ethSendRawTransaction(hexValue).send();
            if (response.hasError()) {
                log.error("Web3j Batch RPC ERROR: {}", response.getError().getMessage());
                throw new RuntimeException("Web3j Batch RPC error: " + response.getError().getMessage());
            }

            String txHash = response.getTransactionHash();
            log.info("🎉 BATCH DISPATCH SUCCESSFUL! Broadcasted {} payouts in SINGLE EVM Tx Hash: {} to Gateway ({})",
                    batchList.size(), txHash, gatewayContractAddress);

            for (Payout payout : batchList) {
                String memoStr = "CHAINPAY:" + payout.getId();
                BigInteger estimatedGasUsed = BigInteger.valueOf(50000L);
                BigInteger totalGasCostWei = gasPrice.multiply(estimatedGasUsed);
                String costInEth = new java.math.BigDecimal(totalGasCostWei).divide(new java.math.BigDecimal("1000000000000000000")).toPlainString() + " ETH";

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

    @Transactional
    public synchronized void processSinglePayout(Payout payout) {
        try {
            log.info("Worker picking up PENDING payout ID {}", payout.getId());
            stateMachine.transition(payout, PayoutStatus.PROCESSING, "Picked up by BlockchainWorker", "BLOCKCHAIN_WORKER");

            Credentials credentials = Credentials.create(privateKey);
            String fromAddress = credentials.getAddress();
            log.info("Hot Wallet initialized for sender address: {}", fromAddress);

            BigInteger rpcNonce = web3j.ethGetTransactionCount(fromAddress, org.web3j.protocol.core.DefaultBlockParameterName.PENDING)
                    .send().getTransactionCount();

            long dbMaxNonce = blockchainTxRepository.findMaxNonceByFromAddress(fromAddress).orElse(-1L);
            long highestKnown = Math.max(rpcNonce.longValue(), dbMaxNonce + 1);
            long nextNonce = Math.max(highestKnown, nonceTracker.get() + 1);
            nonceTracker.set(nextNonce);
            BigInteger nonce = BigInteger.valueOf(nextNonce);
            log.info("Calculated bulletproof EVM Nonce #{} (RPC: {}, DB Max+1: {}) for sender address {}",
                    nonce, rpcNonce, dbMaxNonce + 1, fromAddress);

            BigInteger gasPrice = BigInteger.valueOf(20000000000L);
            BigInteger value = payout.getAmount() != null ? payout.getAmount() : BigInteger.ONE;

            String txHash;
            org.web3j.crypto.RawTransaction rawTx;
            Asset asset = payout.getAsset();

            if (asset != null && asset.getContractAddress() != null 
                    && !asset.getContractAddress().isBlank() 
                    && !asset.getContractAddress().equals("0x0000000000000000000000000000000000000000")) {
                
                org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                        "transfer",
                        java.util.Arrays.asList(
                                new org.web3j.abi.datatypes.Address(payout.getDestinationAddress()),
                                new org.web3j.abi.datatypes.generated.Uint256(value)
                        ),
                        java.util.Collections.emptyList()
                );
                String encodedFunction = org.web3j.abi.FunctionEncoder.encode(function);
                BigInteger contractGasLimit = BigInteger.valueOf(65000L);
                
                rawTx = org.web3j.crypto.RawTransaction.createTransaction(
                        nonce, gasPrice, contractGasLimit, asset.getContractAddress(), BigInteger.ZERO, encodedFunction
                );
                log.info("Encapsulated ERC-20 transfer for asset {} (Contract: {}) with data payload: {}", 
                        asset.getSymbol(), asset.getContractAddress(), encodedFunction);
            } else {
                // Route through ChainPayGateway smart contract!
                byte[] payoutIdBytes = new byte[32];
                byte[] uuidBytes = payout.getId().toString().replace("-", "").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                System.arraycopy(uuidBytes, 0, payoutIdBytes, 0, Math.min(uuidBytes.length, 32));

                org.web3j.abi.datatypes.Function gatewayFunction = new org.web3j.abi.datatypes.Function(
                        "dispatchNativePayout",
                        java.util.Arrays.asList(
                                new org.web3j.abi.datatypes.generated.Bytes32(payoutIdBytes),
                                new org.web3j.abi.datatypes.Address(payout.getDestinationAddress()),
                                new org.web3j.abi.datatypes.Utf8String("CHAINPAY:" + payout.getId())
                        ),
                        java.util.Collections.emptyList()
                );
                String encodedFunction = org.web3j.abi.FunctionEncoder.encode(gatewayFunction);
                BigInteger contractGasLimit = BigInteger.valueOf(100000L);

                rawTx = org.web3j.crypto.RawTransaction.createTransaction(
                        nonce, gasPrice, contractGasLimit, gatewayContractAddress, value, encodedFunction
                );
                log.info("Routed payout ID {} through ChainPayGateway smart contract ({})! Function: dispatchNativePayout, Calldata: {}",
                        payout.getId(), gatewayContractAddress, encodedFunction);
            }

            byte[] signedMessage = org.web3j.crypto.TransactionEncoder.signMessage(rawTx, credentials);
            String hexValue = org.web3j.utils.Numeric.toHexString(signedMessage);

            EthSendTransaction response = web3j.ethSendRawTransaction(hexValue).send();
            if (response.hasError()) {
                log.error("Web3j RPC ethSendRawTransaction ERROR: {}", response.getError().getMessage());
                throw new RuntimeException("Web3j RPC error: " + response.getError().getMessage());
            }

            txHash = response.getTransactionHash();
            log.info("Successfully broadcasted raw transaction to ChainPay Gateway on Anvil EVM node! Tx Hash: {}", txHash);

            String memoStr = "CHAINPAY:" + payout.getId();
            BigInteger estimatedGasUsed = BigInteger.valueOf(100000L);
            BigInteger gasLimit = BigInteger.valueOf(100000L);

            BigInteger totalGasCostWei = gasPrice.multiply(estimatedGasUsed);
            String costInEth = new java.math.BigDecimal(totalGasCostWei).divide(new java.math.BigDecimal("1000000000000000000")).toPlainString() + " ETH";

            BlockchainTransaction tx = BlockchainTransaction.builder()
                    .payout(payout)
                    .txHash(txHash)
                    .fromAddress(fromAddress)
                    .toAddress(payout.getDestinationAddress())
                    .nonce(nonce.longValue())
                    .gasPrice(gasPrice)
                    .gasLimit(gasLimit)
                    .gasUsed(estimatedGasUsed)
                    .txCostEth(costInEth)
                    .onChainMemo(memoStr)
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
}
