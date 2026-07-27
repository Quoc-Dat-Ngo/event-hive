package com.eventhive.bookings;

import java.util.UUID;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record BookingUpdateRequest(
        @Positive(message = "Booking price has to be a positive number") Integer priceCents,

        @Size(min = 7, message = "Booking status must be at least 7 characters") BookingStatus status,
        UUID seatId) {
}
