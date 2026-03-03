package com.chainpay.core.ledger.repository;

import com.chainpay.core.ledger.domain.LedgerTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {
    Optional<LedgerTransaction> findByReferenceId(String referenceId);
}
