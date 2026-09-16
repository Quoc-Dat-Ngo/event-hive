package com.eventhive.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.BAD_GATEWAY)
public class PaymentProcessingException extends RuntimeException {
    public PaymentProcessingException(String msg, Exception e) {
        super(msg, e);
    }
}
