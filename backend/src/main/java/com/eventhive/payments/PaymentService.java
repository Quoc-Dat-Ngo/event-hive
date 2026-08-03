package com.eventhive.payments;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eventhive.bookings.Booking;
import com.eventhive.bookings.BookingRepository;
import com.eventhive.bookings.BookingSummaryDTO;
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

        if (rq.status() != null) {
            payment.setStatus((rq.status()));
        }

        if (rq.refundedAt() != null) {
            payment.setRefundedAt(rq.refundedAt());
        }

        return mapper.apply(payment);
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
