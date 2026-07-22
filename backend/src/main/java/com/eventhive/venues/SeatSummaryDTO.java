package com.eventhive.venues;

import java.util.UUID;

public record SeatSummaryDTO(
        UUID id,
        String seatRow,
        Integer number) {

}
