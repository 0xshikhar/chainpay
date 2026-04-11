package com.chainpay.core.webhook.relay;

import com.chainpay.core.webhook.domain.OutboxEvent;
import com.chainpay.core.webhook.domain.WebhookSubscription;
import com.chainpay.core.webhook.repository.OutboxEventRepository;
import com.chainpay.core.webhook.repository.WebhookSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxRelayJob {

    private final OutboxEventRepository outboxEventRepository;
    private final WebhookSubscriptionRepository webhookSubscriptionRepository;

    private final RestClient restClient = RestClient.builder()
            .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                setConnectTimeout(3000);
                setReadTimeout(5000);
            }})
            .build();

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void relayEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc("PENDING");
        if (pendingEvents.isEmpty()) {
            return;
        }

        List<WebhookSubscription> subscriptions = webhookSubscriptionRepository.findByStatus("ACTIVE");

        for (OutboxEvent event : pendingEvents) {
            log.info("[OUTBOX-RELAY] Relaying event ID {} [{}] to {} active webhook subscribers",
                    event.getId(), event.getEventType(), subscriptions.size());

            boolean allSuccess = true;
            for (WebhookSubscription sub : subscriptions) {
                try {
                    log.info("[OUTBOX-RELAY] Dispatching HTTP POST webhook to {}...", sub.getUrl());
                    restClient.post()
                            .uri(sub.getUrl())
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-ChainPay-Event", event.getEventType())
                            .header("X-ChainPay-Signature", "sha256=" + sub.getSecret())
                            .body(event.getPayload() != null ? event.getPayload() : "{}")
                            .retrieve()
                            .toBodilessEntity();
                    log.info("[OUTBOX-RELAY] Webhook dispatch SUCCESS -> {}", sub.getUrl());
                } catch (Exception ex) {
                    log.warn("[OUTBOX-RELAY] Webhook dispatch FAILED -> {}: {}", sub.getUrl(), ex.getMessage());
                    allSuccess = false;
                }
            }

            // Mark processed if dispatches completed (or if no subscribers were registered)
            event.setStatus("PROCESSED");
            outboxEventRepository.save(event);
        }
    }
}
