package com.eventhive.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.PAYMENT_REQUIRED)
public class PaymentRequiredException extends RuntimeException {
    public PaymentRequiredException(String msg, Exception e) {
        super(msg, e);
    }
}
