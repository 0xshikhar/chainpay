package com.chainpay.core.blockchain.repository;

import com.chainpay.core.blockchain.domain.BlockchainTransaction;
import com.chainpay.core.blockchain.domain.BlockchainTxStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BlockchainTransactionRepository extends JpaRepository<BlockchainTransaction, UUID> {
    Optional<BlockchainTransaction> findByTxHash(String txHash);

    @EntityGraph(attributePaths = {"payout", "payout.account", "payout.asset"})
    Optional<BlockchainTransaction> findByPayoutId(UUID payoutId);

    @EntityGraph(attributePaths = {"payout", "payout.account", "payout.asset"})
    List<BlockchainTransaction> findByStatus(BlockchainTxStatus status);

    @Query("SELECT MAX(t.nonce) FROM BlockchainTransaction t WHERE t.fromAddress = :fromAddress")
    Optional<Long> findMaxNonceByFromAddress(@Param("fromAddress") String fromAddress);
}
