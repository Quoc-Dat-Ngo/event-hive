package com.eventhive.bookings;

public record BookingRegistrationResponse(
        BookingDTO booking,
        String url) {
}
