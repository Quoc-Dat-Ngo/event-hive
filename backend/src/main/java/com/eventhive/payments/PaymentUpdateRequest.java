package com.eventhive.payments;

import java.time.Instant;

import jakarta.validation.constraints.FutureOrPresent;

public record PaymentUpdateRequest(
                PaymentStatus status,

                @FutureOrPresent(message = "Payment refund time must be in the future time or exactly now") Instant refundedAt) {

}
