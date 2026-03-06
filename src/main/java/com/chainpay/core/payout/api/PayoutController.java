package com.chainpay.core.payout.api;

import com.chainpay.core.payout.api.dto.CreatePayoutRequest;
import com.chainpay.core.payout.api.dto.PayoutResponse;
import com.chainpay.core.payout.service.PayoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payouts")
@RequiredArgsConstructor
public class PayoutController {

    private final PayoutService payoutService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<PayoutResponse> createPayout(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreatePayoutRequest request
    ) {
        return ResponseEntity.ok(payoutService.createPayout(idempotencyKey, request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
    public ResponseEntity<PayoutResponse> getPayout(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(payoutService.getPayoutById(id));
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<PayoutResponse> retryPayout(@PathVariable("id") UUID id, Authentication authentication) {
        String actor = authentication != null ? authentication.getName() : "OPERATOR";
        return ResponseEntity.ok(payoutService.retryPayout(id, actor));
    }
}
