package com.eventhive.venues;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eventhive.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VenueService {
    private final VenueRepository venueRepo;
    private final VenueDTOMapper mapper;

    public List<VenueDTO> getAllVenues() {
        return venueRepo.findAll().stream().map(mapper).toList();
    }

    public VenueDTO getVenue(UUID id) {
        return venueRepo.findById(id).map(mapper)
                .orElseThrow(() -> new ResourceNotFoundException("Venue with id not found " + id));
    }

    public VenueDTO addNewVenue(VenueRegistrationRequest request) {
        Venue venue = new Venue(request.name(), request.capacity(), request.location());
        venueRepo.save(venue);
        return mapper.apply(venue);
    }

    @Transactional
    public VenueDTO updateVenue(UUID id, VenueUpdateRequest request) {
        Venue venue = venueRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Venue not found " + id));

        if (request.name() != null) {
            venue.setName(request.name());
        }
        if (request.capacity() != null) {
            venue.setCapacity(request.capacity());
        }
        if (request.location() != null) {
            venue.setLocation(request.location());
        }

        return mapper.apply(venue);
    }

    public void removeVenue(UUID id) {
        venueRepo.deleteById(id);
    }
}
