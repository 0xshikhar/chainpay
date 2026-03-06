package com.chainpay.core.payout.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePayoutRequest {

    @NotNull
    private UUID accountId;

    @NotNull
    private UUID assetId;

    @NotBlank
    private String destinationAddress;

    @NotNull
    @Positive
    private BigInteger amount;
}
