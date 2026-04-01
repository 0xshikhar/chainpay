package com.chainpay.core.blockchain.service;

import com.chainpay.core.blockchain.domain.BlockchainTransaction;
import com.chainpay.core.blockchain.repository.BlockchainTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GasManagementService {

    private final BlockchainTransactionRepository blockchainTransactionRepository;
    private final org.web3j.protocol.Web3j web3j;

    @Value("${chainpay.web3.hot-wallet-private-key}")
    private String privateKey;

    @Value("${chainpay.web3.stuck-threshold-minutes:10}")
    private long stuckThresholdMinutes;

    public BigInteger calculateBumpedGasPrice(BigInteger currentGasPrice) {
        if (currentGasPrice == null || currentGasPrice.equals(BigInteger.ZERO)) {
            try {
                org.web3j.protocol.core.methods.response.EthGasPrice ethGasPrice = web3j.ethGasPrice().send();
                if (!ethGasPrice.hasError() && ethGasPrice.getGasPrice() != null) {
                    currentGasPrice = ethGasPrice.getGasPrice();
                } else {
                    currentGasPrice = BigInteger.valueOf(20000000000L); // 20 Gwei baseline
                }
            } catch (Exception ex) {
                log.warn("Failed to fetch live gas price via Web3j: {}. Falling back to 20 Gwei baseline", ex.getMessage());
                currentGasPrice = BigInteger.valueOf(20000000000L);
            }
        }
        return currentGasPrice.multiply(BigInteger.valueOf(115)).divide(BigInteger.valueOf(100));
    }

    @Scheduled(fixedDelay = 60000) // Run every 1 minute
    @Transactional
    public void monitorAndBumpStuckTransactions() {
        List<BlockchainTransaction> submittedTxs = blockchainTransactionRepository.findByStatus("SUBMITTED");
        Instant now = Instant.now();

        for (BlockchainTransaction tx : submittedTxs) {
            Duration age = Duration.between(tx.getCreatedAt(), now);
            if (age.toMinutes() >= stuckThresholdMinutes) {
                BigInteger oldGas = tx.getGasPrice();
                BigInteger newGas = calculateBumpedGasPrice(oldGas);

                try {
                    org.web3j.crypto.Credentials credentials = org.web3j.crypto.Credentials.create(privateKey);
                    String fromAddress = credentials.getAddress();
                    BigInteger minedNonceCount = web3j.ethGetTransactionCount(fromAddress, org.web3j.protocol.core.DefaultBlockParameterName.LATEST)
                            .send().getTransactionCount();

                    if (minedNonceCount.longValue() > tx.getNonce()) {
                        log.info("Transaction {} (nonce {}) has ALREADY been mined on-chain! Skipping gas bump.", tx.getTxHash(), tx.getNonce());
                        continue;
                    }

                    BigInteger nonce = BigInteger.valueOf(tx.getNonce());
                    BigInteger gasLimit = tx.getGasLimit() != null ? tx.getGasLimit() : BigInteger.valueOf(21000L);

                    org.web3j.crypto.RawTransaction rawTx = org.web3j.crypto.RawTransaction.createEtherTransaction(
                            nonce, newGas, gasLimit, tx.getToAddress(), BigInteger.ONE
                    );

                    byte[] signedMessage = org.web3j.crypto.TransactionEncoder.signMessage(rawTx, credentials);
                    String hexValue = org.web3j.utils.Numeric.toHexString(signedMessage);

                    org.web3j.protocol.core.methods.response.EthSendTransaction response =
                            web3j.ethSendRawTransaction(hexValue).send();

                    if (!response.hasError()) {
                        String newTxHash = response.getTransactionHash();
                        log.warn("GAS BUMPED & RE-BROADCASTED! Stuck Tx {} (nonce {}) bumped from {} to {}. Replacement Tx Hash: {}",
                                tx.getTxHash(), tx.getNonce(), oldGas, newGas, newTxHash);
                        tx.setGasPrice(newGas);
                        tx.setTxHash(newTxHash);
                        blockchainTransactionRepository.save(tx);
                    }
                } catch (Exception ex) {
                    log.error("Failed to re-broadcast bumped transaction for nonce {}: {}", tx.getNonce(), ex.getMessage());
                }
            }
        }
    }
}
