package com.chainpay.core.blockchain.domain;

public enum BlockchainTxStatus {
    PENDING,
    SUBMITTED,
    CONFIRMED,
    REVERTED,
    REORG_ORPHANED,
    FAILED
}
