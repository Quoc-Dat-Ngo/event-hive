package com.eventhive.payments;

import java.time.Instant;
import java.util.UUID;

import com.eventhive.exception.RequestValidationException;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record PaymentRegistrationRequest(
        @NotBlank(message = "Stripe intent id cannot be null nor empty") String stripePaymentIntentId,

        @NotNull(message = "amountCents cannot be null") @PositiveOrZero(message = "Must be a non-negative number") Integer amountCents,

        @NotBlank(message = "Currency shown cannot be null nor empty") @Size(min = 3, max = 3) String currency,

        @NotNull(message = "Payment status cannot be null") PaymentStatus status,

        @FutureOrPresent(message = "Payment refund time must be in the future time or exactly now") Instant purchasedAt,

        @FutureOrPresent(message = "Payment refund time must be in the future time or exactly now") Instant refundedAt,

        @NotNull(message = "Each payment must correspond with exactly one booking   ") UUID bookingId) {
    public PaymentRegistrationRequest {
        if (refundedAt != null && refundedAt.isBefore(purchasedAt)) {
            throw new RequestValidationException("Payment refund time must be strictly after purchase time");
        }
    }

}
