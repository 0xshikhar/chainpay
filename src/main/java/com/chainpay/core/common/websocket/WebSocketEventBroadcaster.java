package com.chainpay.core.common.websocket;

import com.chainpay.core.ops.domain.OperationalIncident;
import com.chainpay.core.payout.api.dto.PayoutResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastPayoutStatusChange(PayoutResponse payout) {
        log.info("WebSocket Broadcast: Payout {} status -> {}", payout.getId(), payout.getStatus());
        messagingTemplate.convertAndSend("/topic/payouts", payout);
    }

    public void broadcastOperationalIncident(OperationalIncident incident) {
        log.info("WebSocket Broadcast: Incident [{}] {}", incident.getSeverity(), incident.getTitle());
        messagingTemplate.convertAndSend("/topic/incidents", incident);
    }

    public void broadcastLedgerEvent(Object eventPayload) {
        log.info("WebSocket Broadcast: Ledger event dispatch");
        messagingTemplate.convertAndSend("/topic/ledger", eventPayload);
    }
}
