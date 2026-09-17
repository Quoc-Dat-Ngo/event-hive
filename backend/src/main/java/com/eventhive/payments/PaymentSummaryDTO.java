package com.eventhive.payments;

import java.time.Instant;
import java.util.UUID;

public record PaymentSummaryDTO(
		UUID id,
		String stripePaymentIntentId,
		Long amountCents,
		String currency,
		PaymentStatus status,
		Instant purchasedAt,
		Instant refundedAt) {

}
