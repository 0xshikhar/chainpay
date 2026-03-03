package com.chainpay.core.ledger.api.dto;

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
public class BalanceResponse {

    private UUID accountId;
    private String accountNumber;
    private String assetSymbol;
    private Integer decimals;
    private BigInteger balanceBaseUnits;
}
