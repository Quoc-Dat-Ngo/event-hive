package com.eventhive.payments;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @PutMapping("/{paymentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public PaymentDTO updatePayment(
            @PathVariable("paymentId") UUID id,
            @Valid @RequestBody PaymentUpdateRequest request) {
        return service.updatePayment(id, request);
    }

    @GetMapping("/{paymentId}/booking")
    @PreAuthorize("hasRole('ADMIN') or @paymentSecurity.isOwner(#id, authentication.token.claims['userId'])")
    public BookingSummaryDTO getBooking(
            @PathVariable("paymentId") UUID id) {
        return service.getBooking(id);
    }
}
