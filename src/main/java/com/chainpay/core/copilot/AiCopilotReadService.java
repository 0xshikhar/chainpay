package com.chainpay.core.copilot;

import com.chainpay.core.blockchain.repository.BlockchainTransactionRepository;
import com.chainpay.core.ledger.repository.AccountRepository;
import com.chainpay.core.payout.domain.PayoutStatus;
import com.chainpay.core.payout.repository.PayoutRepository;
import com.chainpay.core.reconciliation.domain.ReconciliationReport;
import com.chainpay.core.reconciliation.repository.ReconciliationReportRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiCopilotReadService {

    private final PayoutRepository payoutRepository;
    private final AccountRepository accountRepository;
    private final ReconciliationReportRepository reportRepository;
    private final BlockchainTransactionRepository blockchainTransactionRepository;

    @Data
    @Builder
    public static class CopilotSystemSummary {
        private long totalAccounts;
        private Map<String, Integer> payoutStatusCounts;
        private long totalBlockchainTransactions;
        private String latestReconciliationStatus;
        private int totalDiscrepanciesFound;
    }

    @Transactional(readOnly = true)
    public CopilotSystemSummary getSystemSummaryForCopilot() {
        Map<String, Integer> statusCounts = new HashMap<>();
        for (PayoutStatus status : PayoutStatus.values()) {
            statusCounts.put(status.name(), payoutRepository.findByStatus(status).size());
        }

        List<ReconciliationReport> reports = reportRepository.findAll();
        ReconciliationReport latestReport = reports.isEmpty() ? null : reports.get(reports.size() - 1);

        return CopilotSystemSummary.builder()
                .totalAccounts(accountRepository.count())
                .payoutStatusCounts(statusCounts)
                .totalBlockchainTransactions(blockchainTransactionRepository.count())
                .latestReconciliationStatus(latestReport != null ? latestReport.getStatus() : "NO_RUNS")
                .totalDiscrepanciesFound(latestReport != null ? latestReport.getDiscrepancyCount() : 0)
                .build();
    }
}
