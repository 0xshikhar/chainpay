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

    @Value("${chainpay.web3.stuck-threshold-minutes:10}")
    private long stuckThresholdMinutes;

    public BigInteger calculateBumpedGasPrice(BigInteger currentGasPrice) {
        if (currentGasPrice == null) {
            return BigInteger.valueOf(23000000000L); // Default 23 Gwei
        }
        // Multiply gas price by 1.15 (115%) to bump stuck mempool transaction
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
                tx.setGasPrice(newGas);
                log.warn("GAS BUMPING: Transaction {} (nonce {}) stuck for {} min. Bumping gas from {} to {}",
                        tx.getTxHash(), tx.getNonce(), age.toMinutes(), oldGas, newGas);
                blockchainTransactionRepository.save(tx);
            }
        }
    }
}
