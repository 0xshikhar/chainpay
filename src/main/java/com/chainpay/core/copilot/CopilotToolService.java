package com.chainpay.core.copilot;

import com.chainpay.core.common.exception.ResourceNotFoundException;
import com.chainpay.core.ledger.api.dto.BalanceResponse;
import com.chainpay.core.ledger.service.LedgerService;
import com.chainpay.core.payout.api.dto.PayoutResponse;
import com.chainpay.core.payout.service.PayoutService;
import com.chainpay.core.reconciliation.domain.ReconciliationReport;
import com.chainpay.core.reconciliation.job.ReconciliationJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CopilotToolService {

    private final LedgerService ledgerService;
    private final PayoutService payoutService;
    private final ReconciliationJob reconciliationJob;

    @Transactional(readOnly = true)
    public BalanceResponse queryAccountBalance(UUID accountId) {
        log.info("AI Copilot Tool Execution: queryAccountBalance for account ID {}", accountId);
        return ledgerService.getAccountBalance(accountId);
    }

    @Transactional(readOnly = true)
    public PayoutResponse explainPayoutFailureReason(UUID payoutId) {
        log.info("AI Copilot Tool Execution: explainPayoutFailureReason for payout ID {}", payoutId);
        return payoutService.getPayoutById(payoutId);
    }

    @Transactional
    public ReconciliationReport triggerAutomatedReconciliation() {
        log.info("AI Copilot Tool Execution: triggerAutomatedReconciliation");
        return reconciliationJob.runReconciliation();
    }

    @Transactional
    public PayoutResponse retryFailedPayout(UUID payoutId) {
        log.info("AI Copilot Tool Execution: retryFailedPayout for payout ID {}", payoutId);
        return payoutService.retryPayout(payoutId, "AI_OPS_COPILOT");
    }
}
