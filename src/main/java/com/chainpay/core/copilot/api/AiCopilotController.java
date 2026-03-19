package com.chainpay.core.copilot.api;

import com.chainpay.core.copilot.AiCopilotReadService;
import com.chainpay.core.copilot.CopilotToolService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/copilot")
@RequiredArgsConstructor
public class AiCopilotController {

    private final AiCopilotReadService copilotReadService;
    private final CopilotToolService copilotToolService;

    @Data
    public static class CopilotToolRequest {
        private String toolName; // e.g. QUERY_BALANCE, EXPLAIN_FAILURE, TRIGGER_RECONCILIATION
        private UUID targetId;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
    public ResponseEntity<AiCopilotReadService.CopilotSystemSummary> getSystemSummary() {
        return ResponseEntity.ok(copilotReadService.getSystemSummaryForCopilot());
    }

    @PostMapping("/execute")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<?> executeTool(@RequestBody CopilotToolRequest request) {
        if ("QUERY_BALANCE".equalsIgnoreCase(request.getToolName())) {
            return ResponseEntity.ok(copilotToolService.queryAccountBalance(request.getTargetId()));
        } else if ("EXPLAIN_FAILURE".equalsIgnoreCase(request.getToolName())) {
            return ResponseEntity.ok(copilotToolService.explainPayoutFailureReason(request.getTargetId()));
        } else if ("TRIGGER_RECONCILIATION".equalsIgnoreCase(request.getToolName())) {
            return ResponseEntity.ok(copilotToolService.triggerAutomatedReconciliation());
        } else if ("RETRY_PAYOUT".equalsIgnoreCase(request.getToolName())) {
            return ResponseEntity.ok(copilotToolService.retryFailedPayout(request.getTargetId()));
        }
        return ResponseEntity.badRequest().body("Unknown copilot tool name: " + request.getToolName());
    }
}
