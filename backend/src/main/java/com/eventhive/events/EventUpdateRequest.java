package com.eventhive.events;

import java.time.Instant;

public record EventUpdateRequest(
        String title,
        String purpose,
        Instant startsAt,
        Instant endsAt,
        String performer,
        EventStatus status) {

}
