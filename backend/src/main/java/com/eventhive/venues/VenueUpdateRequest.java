package com.eventhive.venues;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record VenueUpdateRequest(
        @Size(min = 3, message = "Venue name must be at least 3 characters") String name,

        @PositiveOrZero(message = "Venue capacity must be explicitly greater than or equal to zero") Integer capacity,

        @Size(min = 3, message = "Venue location must be at least 3 characters") String location) {

}
