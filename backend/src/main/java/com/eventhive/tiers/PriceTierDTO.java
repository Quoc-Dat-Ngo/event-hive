package com.eventhive.tiers;

import java.util.UUID;

public record PriceTierDTO(
        UUID id,
        String name,
        Integer priceCents,
        long seatCount,
        UUID eventId) {
}
