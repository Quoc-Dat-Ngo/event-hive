package com.eventhive.events;

import java.time.Instant;

public record EventRegistrationRequest(
        String title,
        String purpose,
        Instant startsAt,
        Instant endsAt,
        String performer,
        EventStatus status) {

}
