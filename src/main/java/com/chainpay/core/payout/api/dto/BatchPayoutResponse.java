package com.chainpay.core.payout.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchPayoutResponse {
    private String batchIdempotencyKey;
    private int totalSubmitted;
    private List<PayoutResponse> payouts;
}
