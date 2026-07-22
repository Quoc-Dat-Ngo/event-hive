package com.eventhive.venues;

import java.util.UUID;

public record VenueDTO(
        UUID id,
        String name,
        Integer capacity,
        String location) {

}
