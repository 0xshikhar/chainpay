package com.chainpay.core.ops.service;

import com.chainpay.core.ops.domain.OperationalIncident;
import com.chainpay.core.ops.repository.OperationalIncidentRepository;
import com.chainpay.core.payout.domain.Payout;
import com.chainpay.core.payout.domain.PayoutStatus;
import com.chainpay.core.payout.repository.PayoutRepository;
import com.chainpay.core.webhook.repository.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnomalyDetectionServiceTest {

    @Mock
    private PayoutRepository payoutRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private OperationalIncidentRepository incidentRepository;

    @InjectMocks
    private AnomalyDetectionService anomalyDetectionService;

    @Test
    @DisplayName("Spike in FAILED_PERMANENTLY payouts should trigger creation of an OperationalIncident")
    void testScanSystemAnomalies_CreatesIncidentOnFailureSpike() {
        List<Payout> failedPayouts = List.of(
                Payout.builder().status(PayoutStatus.FAILED_PERMANENTLY).build(),
                Payout.builder().status(PayoutStatus.FAILED_PERMANENTLY).build(),
                Payout.builder().status(PayoutStatus.FAILED_PERMANENTLY).build()
        );

        when(payoutRepository.findByStatus(PayoutStatus.FAILED_PERMANENTLY)).thenReturn(failedPayouts);
        when(incidentRepository.findByCategory("HIGH_FAILURE_RATE")).thenReturn(Collections.emptyList());
        when(outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc("PENDING")).thenReturn(Collections.emptyList());

        anomalyDetectionService.scanSystemAnomalies();

        verify(incidentRepository, times(1)).save(any(OperationalIncident.class));
    }
}
