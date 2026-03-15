package com.chainpay.core.reconciliation.job;

import com.chainpay.core.blockchain.domain.BlockchainTransaction;
import com.chainpay.core.blockchain.repository.BlockchainTransactionRepository;
import com.chainpay.core.payout.domain.Payout;
import com.chainpay.core.payout.domain.PayoutStatus;
import com.chainpay.core.payout.repository.PayoutRepository;
import com.chainpay.core.reconciliation.domain.ReconciliationDiscrepancy;
import com.chainpay.core.reconciliation.domain.ReconciliationReport;
import com.chainpay.core.reconciliation.repository.ReconciliationReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationJob {

    private final PayoutRepository payoutRepository;
    private final BlockchainTransactionRepository blockchainTransactionRepository;
    private final ReconciliationReportRepository reportRepository;

    @Scheduled(cron = "0 0 * * * *") // Every hour
    @Transactional
    public ReconciliationReport runReconciliation() {
        log.info("Starting scheduled 3-way Financial & Blockchain Reconciliation Job...");

        ReconciliationReport report = ReconciliationReport.builder()
                .status("PASSED")
                .totalChecked(0)
                .discrepancyCount(0)
                .build();

        List<Payout> completedPayouts = payoutRepository.findByStatus(PayoutStatus.COMPLETED);
        report.setTotalChecked(completedPayouts.size());

        for (Payout payout : completedPayouts) {
            BlockchainTransaction tx = blockchainTransactionRepository.findByPayoutId(payout.getId()).orElse(null);

            if (tx == null) {
                ReconciliationDiscrepancy discrepancy = ReconciliationDiscrepancy.builder()
                        .discrepancyType("MISSING_ON_CHAIN")
                        .description("Payout ID " + payout.getId() + " is marked COMPLETED in ledger but has no blockchain transaction record!")
                        .severity("HIGH")
                        .build();
                report.addDiscrepancy(discrepancy);
            } else if (!"CONFIRMED".equalsIgnoreCase(tx.getStatus())) {
                ReconciliationDiscrepancy discrepancy = ReconciliationDiscrepancy.builder()
                        .discrepancyType("STATUS_MISMATCH")
                        .description("Payout ID " + payout.getId() + " is COMPLETED but on-chain status is " + tx.getStatus())
                        .severity("MEDIUM")
                        .build();
                report.addDiscrepancy(discrepancy);
            }
        }

        ReconciliationReport savedReport = reportRepository.save(report);
        log.info("Reconciliation complete. Status: {}, Total Checked: {}, Discrepancies: {}",
                savedReport.getStatus(), savedReport.getTotalChecked(), savedReport.getDiscrepancyCount());

        return savedReport;
    }
}
