package com.chainpay.core.payout.api.dto;

import com.chainpay.core.payout.domain.PayoutStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutStatusHistoryResponse {
    private UUID id;
    private PayoutStatus fromStatus;
    private PayoutStatus toStatus;
    private String reason;
    private String actor;
    private Instant createdAt;
}
