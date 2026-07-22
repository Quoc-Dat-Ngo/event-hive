package com.eventhive.venues;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record VenueRegistrationRequest(
		@NotBlank(message = "Venue name cannot be blank") @Size(min = 3) String name,

		@PositiveOrZero(message = "Venue capacity must be explicitly greater than or equal to zero") Integer capacity,

		@NotBlank(message = "Venue location cannot be blank") @Size(min = 3) String location) {
}
