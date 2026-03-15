package com.chainpay.core.webhook.relay;

import com.chainpay.core.webhook.domain.OutboxEvent;
import com.chainpay.core.webhook.domain.WebhookSubscription;
import com.chainpay.core.webhook.repository.OutboxEventRepository;
import com.chainpay.core.webhook.repository.WebhookSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxRelayJob {

    private final OutboxEventRepository outboxEventRepository;
    private final WebhookSubscriptionRepository webhookSubscriptionRepository;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void relayEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc("PENDING");
        if (pendingEvents.isEmpty()) {
            return;
        }

        List<WebhookSubscription> subscriptions = webhookSubscriptionRepository.findByStatus("ACTIVE");

        for (OutboxEvent event : pendingEvents) {
            log.info("Relaying outbox event ID {} [{}] to {} active subscribers", event.getId(), event.getEventType(), subscriptions.size());

            for (WebhookSubscription sub : subscriptions) {
                // Simulate HTTP POST dispatch
                log.info("Dispatched webhook event {} to target URL {}", event.getEventType(), sub.getUrl());
            }

            event.setStatus("PROCESSED");
            outboxEventRepository.save(event);
        }
    }
}
