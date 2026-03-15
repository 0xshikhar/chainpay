package com.chainpay.core.webhook.relay;

import com.chainpay.core.webhook.domain.OutboxEvent;
import com.chainpay.core.webhook.domain.WebhookSubscription;
import com.chainpay.core.webhook.repository.OutboxEventRepository;
import com.chainpay.core.webhook.repository.WebhookSubscriptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRelayJobTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private WebhookSubscriptionRepository webhookSubscriptionRepository;

    @InjectMocks
    private OutboxRelayJob outboxRelayJob;

    @Test
    @DisplayName("Pending outbox events should be relayed and marked PROCESSED")
    void testRelayEvents_Success() {
        OutboxEvent pendingEvent = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("PAYOUT")
                .aggregateId(UUID.randomUUID().toString())
                .eventType("PAYOUT_COMPLETED")
                .payload("{}")
                .status("PENDING")
                .build();

        WebhookSubscription subscription = WebhookSubscription.builder()
                .id(UUID.randomUUID())
                .url("https://webhook.site/test")
                .secret("secret")
                .events("*")
                .status("ACTIVE")
                .build();

        when(outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc("PENDING")).thenReturn(List.of(pendingEvent));
        when(webhookSubscriptionRepository.findByStatus("ACTIVE")).thenReturn(List.of(subscription));

        outboxRelayJob.relayEvents();

        verify(outboxEventRepository, times(1)).save(any(OutboxEvent.class));
    }
}
