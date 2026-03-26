package com.chainpay.core.common.websocket;

import com.chainpay.core.payout.api.dto.PayoutResponse;
import com.chainpay.core.payout.domain.PayoutStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WebSocketEventBroadcasterTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private WebSocketEventBroadcaster broadcaster;

    @Test
    @DisplayName("Should broadcast payout status change over STOMP topic /topic/payouts")
    void testBroadcastPayoutStatusChange() {
        PayoutResponse payout = PayoutResponse.builder()
                .id(UUID.randomUUID())
                .status(PayoutStatus.COMPLETED)
                .build();

        broadcaster.broadcastPayoutStatusChange(payout);

        verify(messagingTemplate).convertAndSend(eq("/topic/payouts"), eq(payout));
    }
}
