package com.chainpay.core.ledger.api.dto;

import jakarta.validation.Valid;
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
public class PostTransactionRequest {

    private String referenceId;
    private String description;

    @NotEmpty
    @Valid
    private List<JournalEntryRequest> entries;
}
