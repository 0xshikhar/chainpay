package com.chainpay.core.ledger.api;

import com.chainpay.core.common.exception.ResourceNotFoundException;
import com.chainpay.core.ledger.api.dto.BalanceResponse;
import com.chainpay.core.ledger.api.dto.PostTransactionRequest;
import com.chainpay.core.ledger.domain.Account;
import com.chainpay.core.ledger.domain.AccountStatus;
import com.chainpay.core.ledger.domain.AccountType;
import com.chainpay.core.ledger.domain.Asset;
import com.chainpay.core.ledger.domain.LedgerTransaction;
import com.chainpay.core.ledger.repository.AccountRepository;
import com.chainpay.core.ledger.repository.AssetRepository;
import com.chainpay.core.ledger.service.LedgerService;
import jakarta.validation.Valid;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountRepository accountRepository;
    private final AssetRepository assetRepository;
    private final LedgerService ledgerService;

    @Data
    public static class CreateAccountRequest {
        private String accountNumber;
        private AccountType accountType;
        private UUID assetId;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Account> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        Asset asset = assetRepository.findById(request.getAssetId())
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found with ID: " + request.getAssetId()));

        Account account = Account.builder()
                .accountNumber(request.getAccountNumber())
                .accountType(request.getAccountType())
                .asset(asset)
                .status(AccountStatus.ACTIVE)
                .build();

        return ResponseEntity.ok(accountRepository.save(account));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
    public ResponseEntity<java.util.List<Account>> getAllAccounts() {
        return ResponseEntity.ok(accountRepository.findAll());
    }

    @GetMapping("/lookup/{accountNumber}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
    public ResponseEntity<Account> getAccountByNumber(@PathVariable("accountNumber") String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with number: " + accountNumber));
        return ResponseEntity.ok(account);
    }

    @GetMapping("/{id}/balance")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable("id") UUID accountId) {
        return ResponseEntity.ok(ledgerService.getAccountBalance(accountId));
    }

    @PostMapping("/transactions")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<LedgerTransaction> postTransaction(@Valid @RequestBody PostTransactionRequest request) {
        return ResponseEntity.ok(ledgerService.postTransaction(request));
    }
}
