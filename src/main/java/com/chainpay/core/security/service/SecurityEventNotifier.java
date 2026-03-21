package com.chainpay.core.security.service;

import com.chainpay.core.webhook.service.OutboxPublisherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityEventNotifier {

    private final OutboxPublisherService outboxPublisher;

    @Transactional
    public void notifySecurityAlert(String eventType, String targetId, String details) {
        log.warn("SECURITY ALERT DISPATCH: {} for target {} ({})", eventType, targetId, details);
        outboxPublisher.publishEvent("SECURITY", targetId, eventType,
                String.format("{\"targetId\":\"%s\",\"eventType\":\"%s\",\"details\":\"%s\"}", targetId, eventType, details));
    }
}
