package com.eventhive.tiers;

import java.util.UUID;

/** A venue seat as seen for one event: its tier/price (null if not on sale) and availability. */
public record EventSeatDTO(
        UUID seatId,
        String seatRow,
        Integer number,
        UUID tierId,
        String tierName,
        Integer priceCents,
        boolean available) {
}
