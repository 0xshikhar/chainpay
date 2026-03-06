package com.chainpay.core.payout.service;

import com.chainpay.core.common.exception.IllegalPayoutStateTransitionException;
import com.chainpay.core.payout.domain.Payout;
import com.chainpay.core.payout.domain.PayoutStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class PayoutStateMachine {

    private static final Map<PayoutStatus, Set<PayoutStatus>> VALID_TRANSITIONS = Map.of(
            PayoutStatus.PENDING, EnumSet.of(PayoutStatus.PROCESSING, PayoutStatus.FAILED, PayoutStatus.FAILED_PERMANENTLY),
            PayoutStatus.PROCESSING, EnumSet.of(PayoutStatus.SUBMITTED, PayoutStatus.FAILED, PayoutStatus.FAILED_PERMANENTLY),
            PayoutStatus.SUBMITTED, EnumSet.of(PayoutStatus.CONFIRMING, PayoutStatus.FAILED, PayoutStatus.FAILED_PERMANENTLY),
            PayoutStatus.CONFIRMING, EnumSet.of(PayoutStatus.COMPLETED, PayoutStatus.FAILED, PayoutStatus.FAILED_PERMANENTLY),
            PayoutStatus.FAILED, EnumSet.of(PayoutStatus.RETRYING, PayoutStatus.FAILED_PERMANENTLY),
            PayoutStatus.RETRYING, EnumSet.of(PayoutStatus.PROCESSING, PayoutStatus.FAILED_PERMANENTLY),
            PayoutStatus.COMPLETED, EnumSet.noneOf(PayoutStatus.class),
            PayoutStatus.FAILED_PERMANENTLY, EnumSet.noneOf(PayoutStatus.class)
    );

    public void transition(Payout payout, PayoutStatus targetStatus, String reason, String actor) {
        PayoutStatus currentStatus = payout.getStatus();

        if (currentStatus == targetStatus) {
            log.warn("Payout {} is already in status {}", payout.getId(), targetStatus);
            return;
        }

        Set<PayoutStatus> allowedTargetStates = VALID_TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowedTargetStates.contains(targetStatus)) {
            String errorMsg = String.format("Illegal payout state transition from %s to %s for payout ID %s",
                    currentStatus, targetStatus, payout.getId());
            log.error(errorMsg);
            throw new IllegalPayoutStateTransitionException(errorMsg);
        }

        log.info("Transitioning payout ID {} from {} to {} (reason: {})", payout.getId(), currentStatus, targetStatus, reason);
        payout.addStatusHistory(currentStatus, targetStatus, reason, actor);
    }
}
