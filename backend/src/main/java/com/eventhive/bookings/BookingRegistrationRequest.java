package com.eventhive.bookings;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BookingRegistrationRequest(
		@NotNull(message = "Booking price must be a valid number/integer") @Positive(message = "Booking price has to be a positive number/integer") Integer priceCents,

		@NotNull(message = "This booking must be created for a particular event") UUID eventId,

		@NotNull(message = "This booking must be matched with exactly one seat") UUID seatId) {

}
