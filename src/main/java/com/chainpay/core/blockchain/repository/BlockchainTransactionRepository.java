package com.chainpay.core.blockchain.repository;

import com.chainpay.core.blockchain.domain.BlockchainTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BlockchainTransactionRepository extends JpaRepository<BlockchainTransaction, UUID> {
    Optional<BlockchainTransaction> findByTxHash(String txHash);
    Optional<BlockchainTransaction> findByPayoutId(UUID payoutId);
    List<BlockchainTransaction> findByStatus(String status);

    @org.springframework.data.jpa.repository.Query("SELECT MAX(t.nonce) FROM BlockchainTransaction t WHERE t.fromAddress = :fromAddress")
    Optional<Long> findMaxNonceByFromAddress(@org.springframework.data.repository.query.Param("fromAddress") String fromAddress);
}
