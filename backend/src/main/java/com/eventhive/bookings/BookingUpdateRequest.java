package com.eventhive.bookings;

import java.util.UUID;

import jakarta.validation.constraints.Positive;

public record BookingUpdateRequest(
		@Positive(message = "Booking price has to be a positive number") Integer priceCents,

		BookingStatus status,
		UUID seatId) {
}
