package com.chainpay.core.common.exception;

public class IllegalPayoutStateTransitionException extends RuntimeException {
    public IllegalPayoutStateTransitionException(String message) {
        super(message);
    }
}
