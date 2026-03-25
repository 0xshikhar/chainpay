package com.chainpay.core.benchmark;

import com.chainpay.core.ledger.api.dto.JournalEntryRequest;
import com.chainpay.core.ledger.api.dto.PostTransactionRequest;
import com.chainpay.core.ledger.domain.*;
import com.chainpay.core.ledger.repository.AccountRepository;
import com.chainpay.core.ledger.repository.AssetRepository;
import com.chainpay.core.ledger.repository.JournalEntryRepository;
import com.chainpay.core.ledger.repository.LedgerTransactionRepository;
import com.chainpay.core.ledger.service.LedgerService;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChainPayLoadBenchmarkTest {

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
    @DisplayName("High-concurrency stress test with 20 parallel threads posting balanced transactions")
    void testConcurrentBalancedTransactions_ThreadSafety() throws InterruptedException {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCounter = new AtomicInteger(0);

        when(accountRepository.findById(hotWalletAccount.getId())).thenReturn(Optional.of(hotWalletAccount));
        when(accountRepository.findById(customerAccount.getId())).thenReturn(Optional.of(customerAccount));
        when(assetRepository.findById(usdcAsset.getId())).thenReturn(Optional.of(usdcAsset));
        when(transactionRepository.save(any(LedgerTransaction.class))).thenAnswer(i -> i.getArgument(0));

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    JournalEntryRequest debit = JournalEntryRequest.builder()
                            .accountId(hotWalletAccount.getId())
                            .assetId(usdcAsset.getId())
                            .entryType(EntryType.DEBIT)
                            .amount(BigInteger.valueOf(1000000))
                            .build();

                    JournalEntryRequest credit = JournalEntryRequest.builder()
                            .accountId(customerAccount.getId())
                            .assetId(usdcAsset.getId())
                            .entryType(EntryType.CREDIT)
                            .amount(BigInteger.valueOf(1000000))
                            .build();

                    PostTransactionRequest request = PostTransactionRequest.builder()
                            .referenceId("BENCHMARK-TX-" + index)
                            .description("Concurrency benchmark iteration " + index)
                            .entries(List.of(debit, credit))
                            .build();

                    LedgerTransaction tx = ledgerService.postTransaction(request);
                    if (tx != null) {
                        successCounter.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Concurrency benchmark completed within timeout");
        assertEquals(threadCount, successCounter.get(), "All 20 concurrent balanced transactions succeeded cleanly");
        verify(transactionRepository, times(threadCount)).save(any(LedgerTransaction.class));
    }
}
