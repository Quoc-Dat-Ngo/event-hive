package com.eventhive.events;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.eventhive.exception.RequestValidationException;
import com.eventhive.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EventService {
    private final EventRepository eventRepo;
    private final EventDTOMapper mapper;

    public List<EventDTO> getEvents() {
        return eventRepo.findAll().stream().map(mapper).toList();
    }

    public EventDTO getEvent(UUID id) {
        return eventRepo.findById(id).map(mapper)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found " + id));
    }

    public EventDTO addEvent(EventRegistrationRequest request) {
        Event event = new Event(request.title(), request.purpose(), request.startsAt(), request.endsAt(),
                request.performer(), request.status());
        eventRepo.save(event);

        return mapper.apply(event);
    }

    public EventDTO updateEvent(UUID id, EventUpdateRequest request) {
        Event event = eventRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Event not found " + id));
        boolean changes = false;

        if (request.title() != null && !request.title().equals(event.getTitle())) {
            event.setTitle(request.title());
            changes = true;
        }
        if (request.purpose() != null && !request.purpose().equals(event.getPurpose())) {
            event.setPurpose(request.purpose());
            changes = true;
        }
        if (request.startsAt() != null && !request.startsAt().equals(event.getStartsAt())) {
            event.setStartsAt(request.startsAt());
            changes = true;
        }
        if (request.endsAt() != null && !request.endsAt().equals(event.getEndsAt())) {
            event.setEndsAt(request.endsAt());
            changes = true;
        }
        if (request.performer() != null && !request.performer().equals(event.getPerformer())) {
            event.setPerformer(request.performer());
            changes = true;
        }
        if (request.status() != null && !request.status().name().equals(event.getStatus().name())) {
            event.setStatus(request.status());
            changes = true;
        }

        if (!changes) {
            throw new RequestValidationException("No data changes found");
        }

        eventRepo.save(event);
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
