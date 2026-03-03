package com.chainpay.core.ledger.api.dto;

import com.chainpay.core.ledger.domain.EntryType;
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
public class JournalEntryRequest {

    @NotNull
    private UUID accountId;

    @NotNull
    private UUID assetId;

    @NotNull
    private EntryType entryType;

    @NotNull
    @Positive
    private BigInteger amount;
}
