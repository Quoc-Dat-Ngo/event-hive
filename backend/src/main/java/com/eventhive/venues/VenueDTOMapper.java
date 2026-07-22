package com.eventhive.venues;

import java.util.function.Function;

import org.springframework.stereotype.Service;

@Service
public class VenueDTOMapper implements Function<Venue, VenueDTO> {

    @Override
    public VenueDTO apply(Venue v) {
        return new VenueDTO(v.getId(), v.getName(), v.getCapacity(), v.getLocation());
    }
}
