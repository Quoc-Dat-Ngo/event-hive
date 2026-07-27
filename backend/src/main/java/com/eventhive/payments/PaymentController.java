package com.eventhive.payments;

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

import com.eventhive.bookings.BookingSummaryDTO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService service;

    @GetMapping
    public List<PaymentDTO> getAllPayments() {
        return service.getPayments();
    }

    @GetMapping("/{paymentId}")
    public PaymentDTO getPayment(
            @PathVariable("paymentId") UUID id) {
        return service.getPayment(id);
    }

    @PostMapping
    public PaymentDTO addPayment(
            @Valid @RequestBody PaymentRegistrationRequest request) {
        return service.addPayment(request);
    }

    @PutMapping("/{paymentId}")
    public PaymentDTO updatePayment(
            @PathVariable("paymentId") UUID id,
            @Valid @RequestBody PaymentUpdateRequest request) {
        return service.updatePayment(id, request);
    }

    @DeleteMapping("/{paymentId}")
    public void removePayment(
            @PathVariable("paymentId") UUID id) {
        service.removePayment(id);
    }

    @GetMapping("/{paymentId}/booking")
    public BookingSummaryDTO getBooking(
            @PathVariable("paymentId") UUID id) {
        return service.getBooking(id);
    }
}
