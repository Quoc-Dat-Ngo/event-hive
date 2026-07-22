package com.eventhive.seats;

import java.time.Instant;
import java.util.UUID;

public record SeatDTO(
        UUID id,
        String seatRow,
        Integer number,
        Instant createdAt,
        Instant updatedAt,
        UUID venueId) {

}
