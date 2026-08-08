package com.eventhive.bookings;

import java.util.UUID;

public record BookingSummaryDTO(
		UUID bookingId,
		Integer priceCents,
		BookingStatus status,
		UUID userId,
		UUID eventId,
		UUID seatId) {
}
