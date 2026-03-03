package com.chainpay.core.ledger.repository;

import com.chainpay.core.ledger.domain.Account;
import com.chainpay.core.ledger.domain.AccountType;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByAccountNumber(String accountNumber);
    List<Account> findByAccountTypeAndAssetId(AccountType accountType, UUID assetId);
    boolean existsByAccountNumber(String accountNumber);
}
