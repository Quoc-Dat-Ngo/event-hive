package com.eventhive.tiers;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// Seat ranges are fixed once created; delete and recreate the tier to change them
public record PriceTierUpdateRequest(
        @Size(min = 1, max = 100, message = "Tier name must be 1 to 100 characters") String name,

        @Positive(message = "Tier price has to be a positive number") Integer priceCents) {
}
