package com.chainpay.core.payout.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchPayoutRequest {

    @NotBlank
    private String batchIdempotencyKey;

    @NotEmpty
    @Valid
    private List<CreatePayoutRequest> payouts;
}
