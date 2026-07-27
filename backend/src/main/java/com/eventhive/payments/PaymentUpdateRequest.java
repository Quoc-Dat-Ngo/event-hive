package com.eventhive.payments;

import java.time.Instant;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Size;

public record PaymentUpdateRequest(
        @Size(min = 6, max = 9, message = "Payment status must be at least 6 characters and at most 9 characters") PaymentStatus status,

        @FutureOrPresent(message = "Payment refund time must be in the future time or exactly now") Instant refundedAt) {

}
