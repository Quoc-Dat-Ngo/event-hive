package com.eventhive.events;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {
    private final EventService eventService;

    // TODO: Handle pagination, sorting, filtering
    @GetMapping
    public List<EventDTO> getAllEvents() {
        return eventService.getEvents();
    }

    @GetMapping("/{eventId}")
    public EventDTO getEvent(
            @PathVariable("eventId") UUID id) {
        return eventService.getEvent(id);
    }

    @PostMapping
    public EventDTO addNewEvent(
            @Valid @RequestBody EventRegistrationRequest request) {
        return eventService.addEvent(request);
    }

    @PutMapping("/{eventId}")
    public EventDTO updateEvent(
            @PathVariable("eventId") UUID id,
            @Valid @RequestBody EventUpdateRequest request) {
        return eventService.updateEvent(id, request);
    }

    @DeleteMapping("/{eventId}")
    public void deleteEvent(
            @PathVariable("eventId") UUID id) {
        eventService.removeEvent(id);
    }

    @GetMapping("/{eventId}/host")
    public VenueSummaryDTO getHost(@PathVariable("eventId") UUID id) {
        return eventService.getHost(id);
    }
}
