package com.chainpay.core.payout.domain;

public enum PayoutStatus {
    PENDING,
    PROCESSING,
    SUBMITTED,
    CONFIRMING,
    COMPLETED,
    FAILED,
    RETRYING,
    FAILED_PERMANENTLY
}
