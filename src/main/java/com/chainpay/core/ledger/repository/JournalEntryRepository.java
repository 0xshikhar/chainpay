package com.chainpay.core.ledger.repository;

import com.chainpay.core.ledger.domain.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    List<JournalEntry> findByAccountIdOrderByCreatedAtAsc(UUID accountId);

    @Query("SELECT COALESCE(SUM(CASE WHEN j.entryType = 'CREDIT' THEN j.amount ELSE -j.amount END), 0) " +
           "FROM JournalEntry j WHERE j.account.id = :accountId")
    BigInteger calculateRunningBalance(@Param("accountId") UUID accountId);
}
