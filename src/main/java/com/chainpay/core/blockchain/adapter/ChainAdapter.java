package com.chainpay.core.blockchain.adapter;

import java.math.BigInteger;

public interface ChainAdapter {
    Long getChainId();
    String getChainName();
    BigInteger estimateGasPrice();
    int getRequiredConfirmations();
}
