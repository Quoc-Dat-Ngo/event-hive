package com.eventhive.payments;

import java.time.Instant;
import java.util.UUID;

public record PaymentSummaryDTO(
        UUID id,
        String stripePaymentIntentId,
        Integer amountCents,
        String currency,
        PaymentStatus status,
        Instant purchasedAt,
        Instant createdAt) {

}
