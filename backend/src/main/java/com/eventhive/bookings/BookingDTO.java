package com.eventhive.bookings;

import java.time.Instant;
import java.util.UUID;

public record BookingDTO(
    UUID id,
    Integer priceCents,
    Instant createdAt,
    Instant updatedAt,
    UUID userId,
    UUID eventId,
    UUID seatId
) {
    
}
