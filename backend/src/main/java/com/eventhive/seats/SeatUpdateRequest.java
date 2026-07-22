package com.eventhive.seats;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SeatUpdateRequest(
        @Size(min = 2, max = 2, message = "Seat row is expected to contain 2 alphabetic characters only") String seatRow,
        @Positive(message = "Seat row number must be a positive number staring from 1") Integer number) {
}
