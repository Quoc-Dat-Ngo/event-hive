package com.eventhive.payments;

import java.time.Instant;
import java.util.UUID;

public record PaymentDTO(
        UUID id,
        String stripePaymentIntentId,
        Integer amountCents,
        String currency,
        String status,
        Instant purchasedAt,
        Instant refundedAt,
        Instant createdAt,
        Instant updatedAt,
        UUID bookingId) {

}
