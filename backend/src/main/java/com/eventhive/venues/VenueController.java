package com.eventhive.venues;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/venues")
@RequiredArgsConstructor
public class VenueController {
    private final VenueService service;

    // TODO: Handle pagination, sorting, filtering
    @GetMapping
    public List<VenueDTO> getVenues() {
        return service.getAllVenues();
    }

    @GetMapping("/{venueId}")
    public VenueDTO getVenue(
            @PathVariable("venueId") UUID id) {
        return service.getVenue(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(code = HttpStatus.CREATED)
    public VenueDTO addNewVenue(
            @Valid @RequestBody VenueRegistrationRequest request) {
        return service.addNewVenue(request);
    }

    @PutMapping("/{venueId}")
    @PreAuthorize("hasRole('ADMIN')")
    public VenueDTO updateVenue(
            @PathVariable("venueId") UUID id,
            @Valid @RequestBody VenueUpdateRequest request) {

        return service.updateVenue(id, request);
    }

    @DeleteMapping("/{venueId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(code = HttpStatus.NO_CONTENT)
    public void deleteVenue(
            @PathVariable("venueId") UUID id) {
        service.removeVenue(id);
    }

    @GetMapping("/{venueId}/events")
    public List<EventSummaryDTO> getEvents(
            @PathVariable("venueId") UUID id) {
        return service.getEvents(id);
    }

    @GetMapping("/{venueId}/seats")
    public List<SeatSummaryDTO> getSeats(@PathVariable("venueId") UUID id) {
        return service.getSeats(id);

    }

}
