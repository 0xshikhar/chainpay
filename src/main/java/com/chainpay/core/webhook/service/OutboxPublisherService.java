package com.chainpay.core.webhook.service;

import com.chainpay.core.webhook.domain.OutboxEvent;
import com.chainpay.core.webhook.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPublisherService {

    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public OutboxEvent publishEvent(String aggregateType, String aggregateId, String eventType, String payload) {
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(payload)
                .status("PENDING")
                .build();

        log.info("Transactional Outbox: Appended event {} for aggregate {} [{}]", eventType, aggregateType, aggregateId);
        return outboxEventRepository.save(event);
    }
}
