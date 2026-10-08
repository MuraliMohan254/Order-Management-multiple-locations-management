package com.orders.order_management.core.exception;

public abstract class BaseCustomException extends RuntimeException {

    private final String errorCode;

    public BaseCustomException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public Object getErrorCode() {
        return this.errorCode;
    }
}

