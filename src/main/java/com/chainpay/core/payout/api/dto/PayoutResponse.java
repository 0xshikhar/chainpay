package com.chainpay.core.payout.api.dto;

import com.chainpay.core.payout.domain.PayoutStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutResponse {
    private UUID id;
    private UUID accountId;
    private String accountNumber;
    private UUID assetId;
    private String assetSymbol;
    private String destinationAddress;
    private BigInteger amount;
    private PayoutStatus status;
    private String idempotencyKey;
    private int retryCount;
    private int maxRetries;
    private String errorReason;
    private List<PayoutStatusHistoryResponse> statusHistory;
    private Instant createdAt;
    private Instant updatedAt;
}
