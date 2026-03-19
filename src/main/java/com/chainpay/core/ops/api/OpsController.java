package com.chainpay.core.ops.api;

import com.chainpay.core.common.exception.ResourceNotFoundException;
import com.chainpay.core.ledger.domain.Account;
import com.chainpay.core.ledger.domain.AccountStatus;
import com.chainpay.core.ledger.repository.AccountRepository;
import com.chainpay.core.ops.domain.OperationalIncident;
import com.chainpay.core.ops.repository.OperationalIncidentRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ops")
@RequiredArgsConstructor
public class OpsController {

    private final OperationalIncidentRepository incidentRepository;
    private final AccountRepository accountRepository;

    @Data
    @Builder
    public static class OpsDashboardSummary {
        private long totalAccounts;
        private long openIncidents;
        private List<OperationalIncident> activeIncidents;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<OpsDashboardSummary> getDashboardSummary() {
        List<OperationalIncident> openIncidents = incidentRepository.findByStatus("OPEN");
        return ResponseEntity.ok(OpsDashboardSummary.builder()
                .totalAccounts(accountRepository.count())
                .openIncidents(openIncidents.size())
                .activeIncidents(openIncidents)
                .build());
    }

    @GetMapping("/incidents")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
    public ResponseEntity<List<OperationalIncident>> getIncidents() {
        return ResponseEntity.ok(incidentRepository.findAll());
    }

    @PostMapping("/accounts/{id}/quarantine")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Account> quarantineAccount(@PathVariable("id") UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + accountId));

        account.setStatus(AccountStatus.SUSPENDED);
        return ResponseEntity.ok(accountRepository.save(account));
    }
}
