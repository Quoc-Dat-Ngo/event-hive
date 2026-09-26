package com.eventhive.bookings;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

// The price is not client-controlled: it comes from the seat's price tier for the event
public record BookingRegistrationRequest(
		@NotNull(message = "This booking must be created for a particular event") UUID eventId,

		@NotNull(message = "This booking must be matched with exactly one seat") UUID seatId) {

}
