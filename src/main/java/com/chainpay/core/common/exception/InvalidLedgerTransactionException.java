package com.chainpay.core.common.exception;

public class InvalidLedgerTransactionException extends RuntimeException {
    public InvalidLedgerTransactionException(String message) {
        super(message);
    }
}
