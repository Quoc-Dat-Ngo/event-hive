package com.eventhive.tiers;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Seats from rowFrom to rowTo (inclusive, rows ordered A..Z, AA..ZZ), optionally
 * limited to seat numbers numberFrom..numberTo. Omitted bounds mean "whole row(s)".
 */
public record SeatRange(
        @NotBlank(message = "Starting row is required") @Pattern(regexp = "[A-Za-z]{1,2}", message = "Rows are 1 or 2 letters") String rowFrom,

        @Pattern(regexp = "[A-Za-z]{1,2}", message = "Rows are 1 or 2 letters") String rowTo,

        @Positive(message = "Seat numbers start from 1") Integer numberFrom,

        @Positive(message = "Seat numbers start from 1") Integer numberTo) {
}
