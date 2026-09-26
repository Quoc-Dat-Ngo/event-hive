package com.eventhive.bookings;

public record BookingRegistrationResponse(
        BookingDTO booking,
        String url,
        // true when an existing PENDING booking's checkout was returned instead of a new one
        boolean resumed) {
}
