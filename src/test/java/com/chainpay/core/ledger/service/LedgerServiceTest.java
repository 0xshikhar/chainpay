package com.chainpay.core.ledger.service;

import com.chainpay.core.common.exception.InvalidLedgerTransactionException;
import com.chainpay.core.ledger.api.dto.BalanceResponse;
import com.chainpay.core.ledger.api.dto.JournalEntryRequest;
import com.chainpay.core.ledger.api.dto.PostTransactionRequest;
import com.chainpay.core.ledger.domain.*;
import com.chainpay.core.ledger.repository.AccountRepository;
import com.chainpay.core.ledger.repository.AssetRepository;
import com.chainpay.core.ledger.repository.JournalEntryRepository;
import com.chainpay.core.ledger.repository.LedgerTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private LedgerTransactionRepository transactionRepository;

    @Mock
    private JournalEntryRepository journalEntryRepository;

    @InjectMocks
    private LedgerService ledgerService;

    private Asset usdcAsset;
    private Account hotWalletAccount;
    private Account customerAccount;

    @BeforeEach
    void setUp() {
        usdcAsset = Asset.builder()
                .id(UUID.randomUUID())
                .symbol("USDC")
                .contractAddress("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48")
                .chainId(31337L)
                .decimals(6)
                .build();

        hotWalletAccount = Account.builder()
                .id(UUID.randomUUID())
                .accountNumber("ACC-SYS-HOT-01")
                .accountType(AccountType.SYSTEM_HOT_WALLET)
                .asset(usdcAsset)
                .status(AccountStatus.ACTIVE)
                .build();

        customerAccount = Account.builder()
                .id(UUID.randomUUID())
                .accountNumber("ACC-CUST-1001")
                .accountType(AccountType.CUSTOMER_AVAILABLE)
                .asset(usdcAsset)
                .status(AccountStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("Balanced debit and credit transaction should be saved successfully")
    void testPostBalancedTransaction_Success() {
        BigInteger amount = new BigInteger("10000000"); // 10 USDC

        JournalEntryRequest debitEntry = JournalEntryRequest.builder()
                .accountId(hotWalletAccount.getId())
                .assetId(usdcAsset.getId())
                .entryType(EntryType.DEBIT)
                .amount(amount)
                .build();

        JournalEntryRequest creditEntry = JournalEntryRequest.builder()
                .accountId(customerAccount.getId())
                .assetId(usdcAsset.getId())
                .entryType(EntryType.CREDIT)
                .amount(amount)
                .build();

        PostTransactionRequest request = PostTransactionRequest.builder()
                .referenceId("TX-1001")
                .description("Funding customer balance")
                .entries(List.of(debitEntry, creditEntry))
                .build();

        when(accountRepository.findById(hotWalletAccount.getId())).thenReturn(Optional.of(hotWalletAccount));
        when(accountRepository.findById(customerAccount.getId())).thenReturn(Optional.of(customerAccount));
        when(assetRepository.findById(usdcAsset.getId())).thenReturn(Optional.of(usdcAsset));
        when(transactionRepository.save(any(LedgerTransaction.class))).thenAnswer(i -> i.getArgument(0));

        LedgerTransaction result = ledgerService.postTransaction(request);

        assertNotNull(result);
        assertEquals("TX-1001", result.getReferenceId());
        assertEquals(2, result.getJournalEntries().size());
        verify(transactionRepository, times(1)).save(any(LedgerTransaction.class));
    }

    @Test
    @DisplayName("Unbalanced transaction should throw InvalidLedgerTransactionException")
    void testPostUnbalancedTransaction_ThrowsException() {
        JournalEntryRequest debitEntry = JournalEntryRequest.builder()
                .accountId(hotWalletAccount.getId())
                .assetId(usdcAsset.getId())
                .entryType(EntryType.DEBIT)
                .amount(new BigInteger("10000000"))
                .build();

        JournalEntryRequest creditEntry = JournalEntryRequest.builder()
                .accountId(customerAccount.getId())
                .assetId(usdcAsset.getId())
                .entryType(EntryType.CREDIT)
                .amount(new BigInteger("5000000")) // Unbalanced: 10 != 5
                .build();

        PostTransactionRequest request = PostTransactionRequest.builder()
                .referenceId("TX-UNBALANCED")
                .description("Invalid transaction")
                .entries(List.of(debitEntry, creditEntry))
                .build();

        when(accountRepository.findById(hotWalletAccount.getId())).thenReturn(Optional.of(hotWalletAccount));
        when(accountRepository.findById(customerAccount.getId())).thenReturn(Optional.of(customerAccount));
        when(assetRepository.findById(usdcAsset.getId())).thenReturn(Optional.of(usdcAsset));

        assertThrows(InvalidLedgerTransactionException.class, () -> ledgerService.postTransaction(request));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Calculate account balance should return running balance")
    void testGetAccountBalance_Success() {
        when(accountRepository.findById(customerAccount.getId())).thenReturn(Optional.of(customerAccount));
        when(journalEntryRepository.calculateRunningBalance(customerAccount.getId())).thenReturn(new BigInteger("50000000"));

        BalanceResponse response = ledgerService.getAccountBalance(customerAccount.getId());

        assertNotNull(response);
        assertEquals("USDC", response.getAssetSymbol());
        assertEquals(new BigInteger("50000000"), response.getBalanceBaseUnits());
    }
}
