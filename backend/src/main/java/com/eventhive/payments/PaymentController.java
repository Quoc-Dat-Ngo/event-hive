package com.eventhive.payments;

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

import com.eventhive.bookings.BookingSummaryDTO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService service;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<PaymentDTO> getAllPayments() {
        return service.getPayments();
    }

    @GetMapping("/{paymentId}")
    @PreAuthorize("hasRole('ADMIN') or @paymentSecurity.isOwner(#id, authentication.token.claims['userId'])")
    public PaymentDTO getPayment(
            @PathVariable("paymentId") UUID id) {
        return service.getPayment(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or @bookingSecurity.isOwner(#request.bookingId(), authentication.token.claims['userId'])")
    public PaymentDTO addPayment(
            @Valid @RequestBody PaymentRegistrationRequest request) {
        return service.addPayment(request);
    }

    @PutMapping("/{paymentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public PaymentDTO updatePayment(
            @PathVariable("paymentId") UUID id,
            @Valid @RequestBody PaymentUpdateRequest request) {
        return service.updatePayment(id, request);
    }

    @DeleteMapping("/{paymentId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(code = HttpStatus.NO_CONTENT)
    public void removePayment(
            @PathVariable("paymentId") UUID id) {
        service.removePayment(id);
    }

    @GetMapping("/{paymentId}/booking")
    @PreAuthorize("hasRole('ADMIN') or @paymentSecurity.isOwner(#id, authentication.token.claims['userId'])")
    public BookingSummaryDTO getBooking(
            @PathVariable("paymentId") UUID id) {
        return service.getBooking(id);
    }
}
