package com.eventhive.bookings;

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

import com.eventhive.venues.EventSummaryDTO;
import com.eventhive.venues.SeatSummaryDTO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {
    private final BookingService service;

    @GetMapping
    public List<BookingDTO> getBookings() {
        return service.getBookings();
    }

    @GetMapping("/{bookingId}")
    public BookingDTO getBooking(
            @PathVariable("bookingId") UUID id) {
        return service.getBooking(id);
    }

    @PostMapping
    public BookingDTO addBooking(
            @Valid @RequestBody BookingRegistrationRequest rq) {
        return service.addBooking(rq);
    }

    @PutMapping("/{bookingId}")
    public BookingDTO updateBooking(
            @PathVariable("bookingId") UUID id,
            @Valid @RequestBody BookingUpdateRequest rq) {
        return service.updateBooking(id, rq);
    }

    @DeleteMapping("/{bookingId}")
    public void deleteBooking(
            @PathVariable("bookingId") UUID id) {
        service.removeBooking(id);
    }

    @GetMapping("/{bookingId}/user")
    public UserSummaryDTO getUser(
            @PathVariable("bookingId") UUID id) {
        return service.getUser(id);
    }

    @GetMapping("/{bookingId}/event")
    public EventSummaryDTO getEvent(
            @PathVariable("bookingId") UUID id) {
        return service.getEvent(id);
    }

    @GetMapping("/{bookingId}/seat")
    public SeatSummaryDTO getSeat(
            @PathVariable("bookingId") UUID id) {
        return service.getSeat(id);
    }
}
