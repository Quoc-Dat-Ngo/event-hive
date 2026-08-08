package com.eventhive.payments;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eventhive.bookings.Booking;
import com.eventhive.bookings.BookingRepository;
import com.eventhive.bookings.BookingSummaryDTO;
import com.eventhive.exception.RequestValidationException;
import com.eventhive.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository paymentRepo;
    private final BookingRepository bookingRepo;
    private final PaymentDTOMapper mapper;

    public List<PaymentDTO> getPayments() {
        return paymentRepo.findAll().stream().map(mapper).toList();
    }

    public PaymentDTO getPayment(UUID id) {
        return paymentRepo.findById(id).map(mapper)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found " + id));
    }

    public PaymentDTO addPayment(PaymentRegistrationRequest rq) {
        Booking booking = bookingRepo.findById(rq.bookingId()).orElseThrow(() -> new ResourceNotFoundException(
                "Booking associated with this payment not found " + rq.bookingId()));

        Payment payment = new Payment(rq.stripePaymentIntentId(), rq.amountCents(), rq.currency(), rq.status(),
                booking);

        paymentRepo.save(payment);

        return mapper.apply(payment);
    }

    @Transactional
    public PaymentDTO updatePayment(UUID id, PaymentUpdateRequest rq) {
        Payment payment = paymentRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found " + id));

        // 1. Process Status State Machine transitions securely
        if (rq.status() != null) {
            validateStateTransition(payment.getStatus(), rq.status());
            payment.setStatus(rq.status());
        }

        // 2. Enforce structural alignment for refunds
        if (rq.refundedAt() != null) {
            if (payment.getStatus() != PaymentStatus.REFUNDED) {
                throw new RequestValidationException("Cannot set a refund timestamp unless payment status is REFUNDED");
            }
            payment.setRefundedAt(rq.refundedAt());
        } else if (payment.getStatus() == PaymentStatus.REFUNDED && payment.getRefundedAt() == null) {
            // Fallback: If status changed to REFUNDED but client omitted the time, set it
            // to now
            payment.setRefundedAt(Instant.now());
        }

        return mapper.apply(payment);
    }

    private void validateStateTransition(PaymentStatus current, PaymentStatus incoming) {
        if (current == incoming) {
            return; // No-op if it's the same state
        }

        // Block ANY mutations if the payment is already closed as REFUNDED
        if (current == PaymentStatus.REFUNDED) {
            throw new RequestValidationException("Archived payments cannot be changed from REFUNDED to " + incoming);
        }

        // Block transitions out of a terminal FAILED state
        if (current == PaymentStatus.FAILED) {
            throw new RequestValidationException("Cannot transition a failed payment to " + incoming);
        }
    }

    public void removePayment(UUID id) {
        Payment payment = paymentRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found " + id));
        paymentRepo.delete(payment);
    }

    public BookingSummaryDTO getBooking(UUID paymentId) {
        return paymentRepo.findBookingAssociatedWithPaymentId(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found " + paymentId));
    }
}
