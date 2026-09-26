package com.eventhive.tiers;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PriceTierRegistrationRequest(
        @NotBlank(message = "Tier name cannot be blank") @Size(max = 100) String name,

        @NotNull(message = "Tier price is required") @Positive(message = "Tier price has to be a positive number") Integer priceCents,

        @NotEmpty(message = "A tier needs at least one seat range") List<@Valid SeatRange> seatRanges) {
}
