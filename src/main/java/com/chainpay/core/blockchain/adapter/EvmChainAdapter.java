package com.chainpay.core.blockchain.adapter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigInteger;

@Getter
@Builder
@AllArgsConstructor
public class EvmChainAdapter implements ChainAdapter {

    private final Long chainId;
    private final String chainName;
    private final BigInteger defaultGasPrice;
    private final int requiredConfirmations;

    @Override
    public BigInteger estimateGasPrice() {
        return defaultGasPrice != null ? defaultGasPrice : BigInteger.valueOf(20000000000L);
    }
}
