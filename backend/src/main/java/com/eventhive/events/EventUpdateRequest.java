package com.eventhive.events;

import java.time.Instant;

import com.eventhive.exception.RequestValidationException;

import jakarta.validation.constraints.Size;

public record EventUpdateRequest(
		@Size(min = 3, message = "Event title must be at least 3 characters") String title,

		String purpose,

		Instant startsAt,

		Instant endsAt,

		String performer,

		EventStatus status) {

	public EventUpdateRequest {
		if (startsAt != null && endsAt != null && startsAt.isAfter(endsAt)) {
			throw new RequestValidationException("Event ending time must strictly occur after starting time");
		}
	}
}
