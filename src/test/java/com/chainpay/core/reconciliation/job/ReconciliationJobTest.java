package com.chainpay.core.reconciliation.job;

import com.chainpay.core.blockchain.repository.BlockchainTransactionRepository;
import com.chainpay.core.payout.domain.Payout;
import com.chainpay.core.payout.domain.PayoutStatus;
import com.chainpay.core.payout.repository.PayoutRepository;
import com.chainpay.core.reconciliation.domain.ReconciliationReport;
import com.chainpay.core.reconciliation.repository.ReconciliationReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconciliationJobTest {

    @Mock
    private PayoutRepository payoutRepository;

    @Mock
    private BlockchainTransactionRepository blockchainTransactionRepository;

    @Mock
    private ReconciliationReportRepository reportRepository;

    @Mock
    private com.chainpay.core.ledger.repository.AccountRepository accountRepository;

    @Mock
    private com.chainpay.core.ledger.service.LedgerService ledgerService;

    @Mock
    private org.web3j.protocol.Web3j web3j;

    private ReconciliationJob reconciliationJob;

    @BeforeEach
    void setUp() {
        reconciliationJob = new ReconciliationJob(
                payoutRepository,
                blockchainTransactionRepository,
                reportRepository,
                accountRepository,
                ledgerService,
                web3j,
                "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"
        );
    }

    @Test
    @DisplayName("Reconciliation job should flag missing blockchain transaction as discrepancy")
    void testReconciliationJob_FlagsMissingOnChainTx() {
        Payout payout = Payout.builder()
                .id(UUID.randomUUID())
                .status(PayoutStatus.COMPLETED)
                .build();

        when(payoutRepository.findByStatus(PayoutStatus.COMPLETED)).thenReturn(List.of(payout));
        when(blockchainTransactionRepository.findByPayoutId(payout.getId())).thenReturn(Optional.empty()); // Missing on chain!
        when(reportRepository.save(any(ReconciliationReport.class))).thenAnswer(i -> i.getArgument(0));

        ReconciliationReport report = reconciliationJob.runReconciliation();

        assertNotNull(report);
        assertEquals("DISCREPANCY_FOUND", report.getStatus());
        assertEquals(1, report.getDiscrepancyCount());
        assertEquals("MISSING_ON_CHAIN", report.getDiscrepancies().get(0).getDiscrepancyType());
    }
}
