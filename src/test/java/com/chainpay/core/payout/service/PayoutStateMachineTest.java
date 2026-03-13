package com.chainpay.core.payout.service;

import com.chainpay.core.common.exception.IllegalPayoutStateTransitionException;
import com.chainpay.core.payout.domain.Payout;
import com.chainpay.core.payout.domain.PayoutStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PayoutStateMachineTest {

    private PayoutStateMachine stateMachine;
    private Payout payout;

    @BeforeEach
    void setUp() {
        stateMachine = new PayoutStateMachine();
        payout = Payout.builder()
                .id(UUID.randomUUID())
                .status(PayoutStatus.PENDING)
                .build();
    }

    @Test
    @DisplayName("Legal state transitions sequence should succeed")
    void testLegalStateTransitions_Success() {
        // PENDING -> PROCESSING
        stateMachine.transition(payout, PayoutStatus.PROCESSING, "Worker pickup", "WORKER");
        assertEquals(PayoutStatus.PROCESSING, payout.getStatus());

        // PROCESSING -> SUBMITTED
        stateMachine.transition(payout, PayoutStatus.SUBMITTED, "Broadcasted tx to mempool", "BLOCKCHAIN_WORKER");
        assertEquals(PayoutStatus.SUBMITTED, payout.getStatus());

        // SUBMITTED -> CONFIRMING
        stateMachine.transition(payout, PayoutStatus.CONFIRMING, "Mined in block", "EVENT_LISTENER");
        assertEquals(PayoutStatus.CONFIRMING, payout.getStatus());

        // CONFIRMING -> COMPLETED
        stateMachine.transition(payout, PayoutStatus.COMPLETED, "Accrued 12 confirmations", "CONFIRMATION_TRACKER");
        assertEquals(PayoutStatus.COMPLETED, payout.getStatus());
    }

    @Test
    @DisplayName("Illegal transition from PENDING directly to COMPLETED should throw IllegalPayoutStateTransitionException")
    void testIllegalStateTransition_PendingToCompleted_ThrowsException() {
        assertThrows(IllegalPayoutStateTransitionException.class, () ->
                stateMachine.transition(payout, PayoutStatus.COMPLETED, "Bypassing state machine", "HACKER")
        );
    }

    @Test
    @DisplayName("Terminal failure transition to FAILED_PERMANENTLY should be allowed from any active state")
    void testTransitionToFailedPermanently_Allowed() {
        stateMachine.transition(payout, PayoutStatus.PROCESSING, "Worker pickup", "WORKER");
        stateMachine.transition(payout, PayoutStatus.FAILED_PERMANENTLY, "Contract execution reverted", "EVM_CLIENT");

        assertEquals(PayoutStatus.FAILED_PERMANENTLY, payout.getStatus());
    }

    @Test
    @DisplayName("Transition out of FAILED_PERMANENTLY terminal state should throw IllegalPayoutStateTransitionException")
    void testTransitionOutOfFailedPermanently_ThrowsException() {
        payout.setStatus(PayoutStatus.FAILED_PERMANENTLY);

        assertThrows(IllegalPayoutStateTransitionException.class, () ->
                stateMachine.transition(payout, PayoutStatus.PROCESSING, "Attempting illegal revive", "OPERATOR")
        );
    }
}
