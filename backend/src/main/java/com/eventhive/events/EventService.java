package com.eventhive.events;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.eventhive.exception.ResourceNotFoundException;
import com.eventhive.venues.Venue;
import com.eventhive.venues.VenueRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EventService {
    private final EventRepository eventRepo;
    private final VenueRepository venueRepo;
    private final EventDTOMapper mapper;

    public List<EventDTO> getEvents() {
        return eventRepo.findAll().stream().map(mapper).toList();
    }

    public EventDTO getEvent(UUID id) {
        return eventRepo.findById(id).map(mapper)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found " + id));
    }

    public EventDTO addEvent(EventRegistrationRequest request) {
        Venue venue = venueRepo.findById(request.venueId())
                .orElseThrow(() -> new ResourceNotFoundException("Venue with id not found " + request.venueId()));

        Event event = new Event(request.title(), request.purpose(), request.startsAt(), request.endsAt(),
                request.performer(), request.status(), venue);
        eventRepo.save(event);

        return mapper.apply(event);
    }

    @Transactional
    public EventDTO updateEvent(UUID id, EventUpdateRequest request) {
        Event event = eventRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Event not found " + id));

        if (request.title() != null) {
            event.setTitle(request.title());
        }
        if (request.purpose() != null) {
            event.setPurpose(request.purpose());
        }
        if (request.startsAt() != null) {
            event.setStartsAt(request.startsAt());
        }
        if (request.endsAt() != null) {
            event.setEndsAt(request.endsAt());
        }
        if (request.performer() != null) {
            event.setPerformer(request.performer());
        }
        if (request.status() != null) {
            event.setStatus(request.status());
        }

        return mapper.apply(event);
    }

    public void removeEvent(UUID id) {
        eventRepo.deleteById(id);
    }

    public VenueSummaryDTO getHost(UUID id) {
        return eventRepo.findHostVenueById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found " + id));
    }
}
