package com.eventhive.tiers;

import java.util.UUID;

public record TierSeatAssignment(
        UUID seatId,
        UUID tierId) {
}
