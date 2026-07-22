package com.eventhive.seats;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SeatRegistrationRequest(
        @NotBlank(message = "Seat row cannot be blank") @Size(min = 2, max = 2, message = "Seat row is expected to contain 2 alphabetic characters only") String seatRow,

        @NotNull(message = "Seat row cannot be null") @Positive(message = "Seat row number must be a positive number staring from 1") Integer number,

        @NotNull(message = "Venue ID to reference cannot be null") UUID venueId) {
}
