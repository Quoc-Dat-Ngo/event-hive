package com.eventhive.payments;

import java.time.Instant;

import jakarta.validation.constraints.PastOrPresent;

public record PaymentUpdateRequest(
        PaymentStatus status,

        @PastOrPresent(message = "Payment refund time cannot be a future date") Instant refundedAt) {
}
