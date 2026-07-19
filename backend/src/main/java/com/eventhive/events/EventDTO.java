package com.eventhive.events;

import java.time.Instant;
import java.util.UUID;

public record EventDTO(
        UUID id,
        String title,
        String purpose,
        Instant startsAt,
        Instant endsAt,
        String performer,
        String status,
        Instant createdAt,
        Instant updatedAt) {
}