package com.eventhive.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.CONFLICT)
public class SeatAlreadyLockedException extends RuntimeException {
    public SeatAlreadyLockedException(String msg) {
        super(msg);
    }
}
