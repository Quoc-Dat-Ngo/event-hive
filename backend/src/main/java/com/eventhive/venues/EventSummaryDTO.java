package com.eventhive.venues;

import java.time.Instant;

import com.eventhive.events.EventStatus;

public record EventSummaryDTO(
		String title,
		String purpose,
		Instant startsAt,
		Instant endsAt,
		String performer,
		EventStatus status) {
}
