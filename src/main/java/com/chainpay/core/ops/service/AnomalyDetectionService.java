package com.chainpay.core.ops.service;

import com.chainpay.core.ops.domain.OperationalIncident;
import com.chainpay.core.ops.repository.OperationalIncidentRepository;
import com.chainpay.core.payout.domain.Payout;
import com.chainpay.core.payout.domain.PayoutStatus;
import com.chainpay.core.payout.repository.PayoutRepository;
import com.chainpay.core.webhook.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnomalyDetectionService {

    private final PayoutRepository payoutRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OperationalIncidentRepository incidentRepository;

    @Scheduled(fixedDelay = 60000) // Every 1 minute
    @Transactional
    public void scanSystemAnomalies() {
        log.info("Running Autonomous Anomaly Detection scan...");

        // 1. Check for spike in FAILED_PERMANENTLY payouts
        List<Payout> failedPermanently = payoutRepository.findByStatus(PayoutStatus.FAILED_PERMANENTLY);
        if (failedPermanently.size() >= 3) {
            log.warn("ANOMALY DETECTED: High count of FAILED_PERMANENTLY payouts ({})", failedPermanently.size());
            boolean existingIncident = incidentRepository.findByCategory("HIGH_FAILURE_RATE")
                    .stream().anyMatch(i -> "OPEN".equals(i.getStatus()));

            if (!existingIncident) {
                OperationalIncident incident = OperationalIncident.builder()
                        .title("High Payout Permanent Failure Spike")
                        .category("HIGH_FAILURE_RATE")
                        .severity("HIGH")
                        .description("System detected " + failedPermanently.size() + " payouts in FAILED_PERMANENTLY state.")
                        .status("OPEN")
                        .build();

                incidentRepository.save(incident);
            }
        }

        // 2. Check for outbox backlog
        long pendingOutboxCount = outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc("PENDING").size();
        if (pendingOutboxCount >= 50) {
            log.warn("ANOMALY DETECTED: Transactional Outbox backlog exceeds 50 events!");
            OperationalIncident incident = OperationalIncident.builder()
                    .title("Transactional Outbox Delivery Backlog")
                    .category("OUTBOX_BACKLOG")
                    .severity("MEDIUM")
                    .description("Pending outbox events saturated max batch size (" + pendingOutboxCount + ").")
                    .status("OPEN")
                    .build();

            incidentRepository.save(incident);
        }
    }
}
