package com.eventhive.venues;

import java.time.Instant;
import java.util.UUID;

public record EventSummaryDTO(
        UUID id,
        String title,
        String purpose,
        Instant startsAt,
        Instant endsAt,
        String performer,
        String status) {
}
