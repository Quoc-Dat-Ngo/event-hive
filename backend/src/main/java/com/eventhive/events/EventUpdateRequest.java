package com.eventhive.events;

import java.time.Instant;

import com.eventhive.exception.RequestValidationException;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Size;

public record EventUpdateRequest(
		@Size(min = 3, message = "Event title must be at least 3 characters") String title,

		String purpose,

		@Future(message = "Event starting time must be in the future time") Instant startsAt,

		@Future(message = "Event ending time must be in the future time") Instant endsAt,

		String performer,

		@Size(min = 5, message = "Event title must be at least 3 characters") EventStatus status) {

	public EventUpdateRequest {
		if (startsAt != null && endsAt != null && startsAt.isAfter(endsAt)) {
			throw new RequestValidationException("Event ending time must strictly occur after starting time");
		}
	}
}
