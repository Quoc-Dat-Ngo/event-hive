package com.eventhive.events;

import java.time.Instant;
import java.util.UUID;

import com.eventhive.exception.RequestValidationException;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EventRegistrationRequest(
		@NotBlank(message = "Event title cannot be blank") @Size(min = 3) String title,

		String purpose,

		@NotNull(message = "Event starting time cannot be null and empty") @Future(message = "Event starting time must be in the future time") Instant startsAt,

		@NotNull(message = "Event ending time cannot be null and empty") @Future(message = "Event ending time must be in the future time") Instant endsAt,

		String performer,

		@NotNull(message = "Event status cannot be blank") EventStatus status,

		@NotNull(message = "Venue ID cannot be blank") UUID venueId) {

	public EventRegistrationRequest {
		if (startsAt.isAfter(endsAt)) {
			throw new RequestValidationException("Event ending time must strictly occur after starting time");
		}
	}
}
