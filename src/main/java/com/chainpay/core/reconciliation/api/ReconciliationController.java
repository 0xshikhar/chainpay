package com.chainpay.core.reconciliation.api;

import com.chainpay.core.reconciliation.domain.ReconciliationReport;
import com.chainpay.core.reconciliation.job.ReconciliationJob;
import com.chainpay.core.reconciliation.repository.ReconciliationReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

    private final ReconciliationReportRepository reportRepository;
    private final ReconciliationJob reconciliationJob;

    @GetMapping("/reports")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
    public ResponseEntity<List<ReconciliationReport>> getReports() {
        return ResponseEntity.ok(reportRepository.findAll());
    }

    @PostMapping("/trigger")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<ReconciliationReport> triggerReconciliation() {
        return ResponseEntity.ok(reconciliationJob.runReconciliation());
    }
}
