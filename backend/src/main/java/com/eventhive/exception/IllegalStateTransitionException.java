package com.eventhive.exception;

public class IllegalStateTransitionException extends RuntimeException {
    public IllegalStateTransitionException(String msg) {
        super(msg);
    }
}
