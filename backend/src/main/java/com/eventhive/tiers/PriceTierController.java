package com.eventhive.tiers;

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
@RequestMapping("/api/v1/events/{eventId}")
@RequiredArgsConstructor
public class PriceTierController {
    private final PriceTierService service;

    @GetMapping("/tiers")
    public List<PriceTierDTO> getTiers(@PathVariable("eventId") UUID eventId) {
        return service.getTiers(eventId);
    }

    @PostMapping("/tiers")
    @PreAuthorize("hasAnyRole('ADMIN', 'EVENT_ORGANISER')")
    @ResponseStatus(code = HttpStatus.CREATED)
    public PriceTierDTO addTier(
            @PathVariable("eventId") UUID eventId,
            @Valid @RequestBody PriceTierRegistrationRequest request) {
        return service.addTier(eventId, request);
    }

    @PutMapping("/tiers/{tierId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EVENT_ORGANISER')")
    public PriceTierDTO updateTier(
            @PathVariable("eventId") UUID eventId,
            @PathVariable("tierId") UUID tierId,
            @Valid @RequestBody PriceTierUpdateRequest request) {
        return service.updateTier(eventId, tierId, request);
    }

    @DeleteMapping("/tiers/{tierId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EVENT_ORGANISER')")
    @ResponseStatus(code = HttpStatus.NO_CONTENT)
    public void deleteTier(
            @PathVariable("eventId") UUID eventId,
            @PathVariable("tierId") UUID tierId) {
        service.deleteTier(eventId, tierId);
    }

    @GetMapping("/seats")
    public List<EventSeatDTO> getSeatMap(@PathVariable("eventId") UUID eventId) {
        return service.getSeatMap(eventId);
    }
}
