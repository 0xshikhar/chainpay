package com.chainpay.core.blockchain.service;

import com.chainpay.core.blockchain.domain.BlockchainTransaction;
import com.chainpay.core.blockchain.domain.BlockchainTxStatus;
import com.chainpay.core.blockchain.repository.BlockchainTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GasManagementService {

    private final BlockchainTransactionRepository blockchainTransactionRepository;
    private final Web3j web3j;
    private final String hotWalletAddress;

    @Value("${chainpay.web3.hot-wallet-private-key}")
    private String privateKey;

    @Value("${chainpay.web3.stuck-threshold-minutes:10}")
    private long stuckThresholdMinutes;

    public BigInteger calculateBumpedGasPrice(BigInteger currentGasPrice) {
        if (currentGasPrice == null || currentGasPrice.equals(BigInteger.ZERO)) {
            try {
                EthGasPrice ethGasPrice = web3j.ethGasPrice().send();
                if (!ethGasPrice.hasError() && ethGasPrice.getGasPrice() != null) {
                    currentGasPrice = ethGasPrice.getGasPrice();
                } else {
                    currentGasPrice = BigInteger.valueOf(20_000_000_000L); // 20 Gwei baseline
                }
            } catch (Exception ex) {
                log.warn("[GAS-MGR] Failed to fetch live gas price via Web3j: {}. Falling back to 20 Gwei baseline", ex.getMessage());
                currentGasPrice = BigInteger.valueOf(20_000_000_000L);
            }
        }
        return currentGasPrice.multiply(BigInteger.valueOf(115)).divide(BigInteger.valueOf(100));
    }

    @Scheduled(fixedDelay = 60000) // Run every 1 minute
    @Transactional
    public void monitorAndBumpStuckTransactions() {
        List<BlockchainTransaction> submittedTxs = blockchainTransactionRepository.findByStatus(BlockchainTxStatus.SUBMITTED);
        Instant now = Instant.now();

        for (BlockchainTransaction tx : submittedTxs) {
            Duration age = Duration.between(tx.getCreatedAt(), now);
            if (age.toMinutes() >= stuckThresholdMinutes) {
                BigInteger oldGas = tx.getGasPrice();
                BigInteger newGas = calculateBumpedGasPrice(oldGas);

                try {
                    BigInteger minedNonceCount = web3j.ethGetTransactionCount(hotWalletAddress, DefaultBlockParameterName.LATEST)
                            .send().getTransactionCount();

                    if (minedNonceCount.longValue() > tx.getNonce()) {
                        log.info("[GAS-MGR] Tx {} (nonce {}) already mined on-chain — skipping gas bump.",
                                tx.getTxHash(), tx.getNonce());
                        continue;
                    }

                    BigInteger nonce = BigInteger.valueOf(tx.getNonce());
                    BigInteger gasLimit = tx.getGasLimit() != null ? tx.getGasLimit() : BigInteger.valueOf(21000L);

                    String storedCalldata = tx.getCalldata();
                    BigInteger storedValue = tx.getValueSentWei() != null ? tx.getValueSentWei() : BigInteger.ZERO;

                    RawTransaction rawTx;
                    if (storedCalldata != null && !storedCalldata.isBlank()) {
                        rawTx = RawTransaction.createTransaction(
                                nonce, newGas, gasLimit, tx.getToAddress(), storedValue, storedCalldata
                        );
                        log.warn("[GAS-MGR] Re-broadcasting stuck contract tx {} (nonce {}) with bumped gas: {} -> {} Gwei. Calldata preserved.",
                                tx.getTxHash(), tx.getNonce(),
                                oldGas.divide(BigInteger.valueOf(1_000_000_000L)),
                                newGas.divide(BigInteger.valueOf(1_000_000_000L)));
                    } else {
                        rawTx = RawTransaction.createEtherTransaction(
                                nonce, newGas, gasLimit, tx.getToAddress(), storedValue
                        );
                        log.warn("[GAS-MGR] Re-broadcasting stuck plain ETH tx {} (nonce {}) with bumped gas: {} -> {} Gwei.",
                                tx.getTxHash(), tx.getNonce(),
                                oldGas.divide(BigInteger.valueOf(1_000_000_000L)),
                                newGas.divide(BigInteger.valueOf(1_000_000_000L)));
                    }

                    Credentials credentials = Credentials.create(privateKey);
                    byte[] signedMessage = TransactionEncoder.signMessage(rawTx, credentials);
                    String hexValue = Numeric.toHexString(signedMessage);

                    EthSendTransaction response = web3j.ethSendRawTransaction(hexValue).send();

                    if (!response.hasError()) {
                        String newTxHash = response.getTransactionHash();
                        log.warn("[GAS-MGR] Gas bump successful. Old tx: {} -> Replacement tx: {} (nonce {}, gas {} Gwei)",
                                tx.getTxHash(), newTxHash, tx.getNonce(),
                                newGas.divide(BigInteger.valueOf(1_000_000_000L)));
                        tx.setGasPrice(newGas);
                        tx.setTxHash(newTxHash);
                        blockchainTransactionRepository.save(tx);
                    } else {
                        log.error("[GAS-MGR] Gas bump RPC failed for nonce {}: {}",
                                tx.getNonce(), response.getError().getMessage());
                    }
                } catch (Exception ex) {
                    log.error("[GAS-MGR] Failed to re-broadcast bumped transaction for nonce {}: {}",
                            tx.getNonce(), ex.getMessage());
                }
            }
        }
    }
}
