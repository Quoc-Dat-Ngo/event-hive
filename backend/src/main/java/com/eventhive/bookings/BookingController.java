package com.eventhive.bookings;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.eventhive.exception.RequestValidationException;
import com.eventhive.payments.PaymentSummaryDTO;
import com.eventhive.security.UserPrincipal;
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
    @PreAuthorize("hasRole('ADMIN')")
    public List<BookingDTO> getBookings() {
        return service.getBookings();
    }

    @GetMapping("/{bookingId}")
    @PreAuthorize("hasRole('ADMIN') or @bookingSecurity.isOwner(#id, authentication.token.claims['userId'])")
    public BookingDTO getBooking(
            @PathVariable("bookingId") UUID id) {
        return service.getBooking(id);
    }

    @PostMapping
    @ResponseStatus(code = HttpStatus.CREATED)
    public BookingDTO addBooking(
            @Valid @RequestBody BookingRegistrationRequest rq,
            @AuthenticationPrincipal Jwt jwt) {
        String claimId = jwt.getClaimAsString("userId");

        if (claimId == null) {
            throw new RequestValidationException("User identity claim missing from authorization token context");
        }

        UUID verifiedUserId = UUID.fromString(claimId);
        return service.addBooking(rq, verifiedUserId);
    }

    @PutMapping("/{bookingId}")
    @PreAuthorize("hasRole('ADMIN') or @bookingSecurity.isOwner(#id, authentication.token.claims['userId'])")
    public BookingDTO updateBooking(
            @PathVariable("bookingId") UUID id,
            @Valid @RequestBody BookingUpdateRequest rq) {
        return service.updateBooking(id, rq);
    }

    @DeleteMapping("/{bookingId}")
    @PreAuthorize("hasRole('ADMIN') or @bookingSecurity.isOwner(#id, authentication.token.claims['userId'])")
    @ResponseStatus(code = HttpStatus.NO_CONTENT)
    public void deleteBooking(
            @PathVariable("bookingId") UUID id) {
        service.removeBooking(id);
    }

    @GetMapping("/{bookingId}/user")
    @PreAuthorize("hasRole('ADMIN') or @bookingSecurity.isOwner(#id, authentication.token.claims['userId'])")
    public UserSummaryDTO getUser(
            @PathVariable("bookingId") UUID id) {
        return service.getUser(id);
    }

    @GetMapping("/{bookingId}/event")
    @PreAuthorize("hasRole('ADMIN') or @bookingSecurity.isOwner(#id, authentication.token.claims['userId'])")
    public EventSummaryDTO getEvent(
            @PathVariable("bookingId") UUID id) {
        return service.getEvent(id);
    }

    @GetMapping("/{bookingId}/seat")
    @PreAuthorize("hasRole('ADMIN') or @bookingSecurity.isOwner(#id, authentication.token.claims['userId'])")
    public SeatSummaryDTO getSeat(
            @PathVariable("bookingId") UUID id) {
        return service.getSeat(id);
    }

    @GetMapping("/{bookingId}/payments")
    @PreAuthorize("hasRole('ADMIN') or @bookingSecurity.isOwner(#id, authentication.token.claims['userId'])")
    public List<PaymentSummaryDTO> getAllPayments(
            @PathVariable("bookingId") UUID id) {
        return service.getAllPayments(id);
    }
}
