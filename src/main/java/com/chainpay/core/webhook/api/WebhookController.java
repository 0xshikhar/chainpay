package com.chainpay.core.webhook.api;

import com.chainpay.core.webhook.domain.WebhookSubscription;
import com.chainpay.core.webhook.repository.WebhookSubscriptionRepository;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookSubscriptionRepository subscriptionRepository;

    @Data
    public static class CreateSubscriptionRequest {
        private String url;
        private String secret;
        private String events;
    }

    @PostMapping("/subscriptions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WebhookSubscription> createSubscription(@Valid @RequestBody CreateSubscriptionRequest request) {
        WebhookSubscription subscription = WebhookSubscription.builder()
                .url(request.getUrl())
                .secret(request.getSecret())
                .events(request.getEvents() != null ? request.getEvents() : "*")
                .status("ACTIVE")
                .build();

        return ResponseEntity.ok(subscriptionRepository.save(subscription));
    }

    @GetMapping("/subscriptions")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<List<WebhookSubscription>> getSubscriptions() {
        return ResponseEntity.ok(subscriptionRepository.findAll());
    }
}
