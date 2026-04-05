package com.chainpay.core.ledger.service;

import com.chainpay.core.common.exception.InvalidLedgerTransactionException;
import com.chainpay.core.common.exception.ResourceNotFoundException;
import com.chainpay.core.ledger.api.dto.BalanceResponse;
import com.chainpay.core.ledger.api.dto.JournalEntryRequest;
import com.chainpay.core.ledger.api.dto.PostTransactionRequest;
import com.chainpay.core.ledger.domain.*;
import com.chainpay.core.ledger.repository.AccountRepository;
import com.chainpay.core.ledger.repository.AssetRepository;
import com.chainpay.core.ledger.repository.JournalEntryRepository;
import com.chainpay.core.ledger.repository.LedgerTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final AccountRepository accountRepository;
    private final AssetRepository assetRepository;
    private final LedgerTransactionRepository transactionRepository;
    private final JournalEntryRepository journalEntryRepository;

    @Transactional
    public LedgerTransaction postTransaction(PostTransactionRequest request) {
        if (request.getEntries() == null || request.getEntries().size() < 2) {
            throw new InvalidLedgerTransactionException("A double-entry transaction must contain at least two entries (one debit, one credit).");
        }

        // Group amounts by asset to enforce asset-specific zero-sum debit == credit invariant
        Map<UUID, BigInteger> assetBalanceMap = new HashMap<>();

        LedgerTransaction transaction = LedgerTransaction.builder()
                .referenceId(request.getReferenceId())
                .description(request.getDescription())
                .build();

        for (JournalEntryRequest entryReq : request.getEntries()) {
            Account account = accountRepository.findById(entryReq.getAccountId())
                    .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + entryReq.getAccountId()));

            Asset asset = assetRepository.findById(entryReq.getAssetId())
                    .orElseThrow(() -> new ResourceNotFoundException("Asset not found with ID: " + entryReq.getAssetId()));

            // Verify account status is ACTIVE (P1 fix: reject journal entries against SUSPENDED/QUARANTINED accounts)
            if (account.getStatus() != AccountStatus.ACTIVE) {
                throw new InvalidLedgerTransactionException("Cannot post transaction to account " +
                        account.getAccountNumber() + " in status " + account.getStatus());
            }

            // Verify account asset matches entry asset
            if (!account.getAsset().getId().equals(asset.getId())) {
                throw new InvalidLedgerTransactionException("Account asset (" + account.getAsset().getSymbol() +
                        ") does not match journal entry asset (" + asset.getSymbol() + ").");
            }

            BigInteger currentDelta = assetBalanceMap.getOrDefault(asset.getId(), BigInteger.ZERO);
            if (entryReq.getEntryType() == EntryType.CREDIT) {
                assetBalanceMap.put(asset.getId(), currentDelta.add(entryReq.getAmount()));
            } else { // DEBIT
                assetBalanceMap.put(asset.getId(), currentDelta.subtract(entryReq.getAmount()));
            }

            JournalEntry entry = JournalEntry.builder()
                    .account(account)
                    .asset(asset)
                    .entryType(entryReq.getEntryType())
                    .amount(entryReq.getAmount())
                    .build();

            transaction.addJournalEntry(entry);
        }

        // Zero-sum invariant enforcement: sum(CREDIT) - sum(DEBIT) must be ZERO for every asset
        for (Map.Entry<UUID, BigInteger> balanceEntry : assetBalanceMap.entrySet()) {
            if (!balanceEntry.getValue().equals(BigInteger.ZERO)) {
                throw new InvalidLedgerTransactionException("Transaction is unbalanced for asset " +
                        balanceEntry.getKey() + ". Discrepancy delta: " + balanceEntry.getValue());
            }
        }

        log.info("Persisting balanced ledger transaction reference: {}", request.getReferenceId());
        return transactionRepository.save(transaction);
    }

    @Transactional(readOnly = true)
    public BalanceResponse getAccountBalance(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + accountId));

        BigInteger balance = journalEntryRepository.calculateRunningBalance(accountId);

        return BalanceResponse.builder()
                .accountId(account.getId())
                .accountNumber(account.getAccountNumber())
                .assetSymbol(account.getAsset().getSymbol())
                .decimals(account.getAsset().getDecimals())
                .balanceBaseUnits(balance)
                .build();
    }
}
