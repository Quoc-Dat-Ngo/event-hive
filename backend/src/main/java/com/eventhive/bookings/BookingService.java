package com.eventhive.bookings;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eventhive.events.Event;
import com.eventhive.events.EventRepository;
import com.eventhive.exception.IllegalStateTransitionException;
import com.eventhive.exception.ResourceNotFoundException;
import com.eventhive.exception.SeatAlreadyLockedException;
import com.eventhive.payments.Payment;
import com.eventhive.payments.PaymentRepository;
import com.eventhive.payments.PaymentStatus;
import com.eventhive.payments.PaymentSummaryDTO;
import com.eventhive.redis.SeatLockService;
import com.eventhive.seats.Seat;
import com.eventhive.seats.SeatRepository;
import com.eventhive.stripe.StripeHostedCheckoutService;
import com.eventhive.stripe.StripeService;
import com.eventhive.users.User;
import com.eventhive.users.UserRepository;
import com.eventhive.venues.EventSummaryDTO;
import com.eventhive.venues.SeatSummaryDTO;
import com.stripe.model.checkout.Session;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookingService {
    private final BookingRepository repo;
    private final UserRepository userRepo;
    private final EventRepository eventRepo;
    private final SeatRepository seatRepo;
    private final PaymentRepository paymentRepo;
    private final BookingDTOMapper mapper;
    private final SeatLockService seatLockService;
    private final StripeHostedCheckoutService checkoutService;
    private final StripeService stripeService;

    private static final Logger logger = LoggerFactory.getLogger(BookingService.class);

    public List<BookingDTO> getBookings() {
        return repo.findAll().stream().map(mapper).toList();
    }

    public BookingDTO getBooking(UUID id) {
        return repo.findById(id).map(mapper)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found " + id));
    }

    private Seat findSeat(UUID id) {
        return seatRepo.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Seat associted with this booking not found " + id));
    }

    public BookingRegistrationResponse addBooking(
            BookingRegistrationRequest rq,
            UUID verifiedUserId) {
        User user = userRepo.findById(verifiedUserId).orElseThrow(
                () -> new ResourceNotFoundException("User associted with this booking not found " + verifiedUserId));
        Event event = eventRepo.findById(rq.eventId()).orElseThrow(
                () -> new ResourceNotFoundException("Event associted with this booking not found " + rq.eventId()));
        Seat seat = findSeat(rq.seatId());

        if (!seatLockService.tryLock(rq.seatId(), rq.eventId(), verifiedUserId)) {
            throw new SeatAlreadyLockedException("Seat is currently reserved by another customer " + rq.seatId());
        }

        try {
            Booking booking = new Booking(
                    rq.priceCents(),
                    BookingStatus.PENDING,
                    user,
                    event,
                    seat);

            repo.save(booking);

            // Handle payment here
            Session session = checkoutService.checkout(booking);

            return new BookingRegistrationResponse(mapper.apply(booking), session.getUrl());
        } catch (RuntimeException ex) {
            seatLockService.releaseLock(rq.seatId(), rq.eventId(), verifiedUserId);
            throw ex;
        }
    }

    @Transactional
    public BookingDTO updateBooking(
            UUID id,
            BookingUpdateRequest rq) {
        Booking booking = repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Booking not found " + id));

        if (rq.priceCents() != null) {
            booking.setPriceCents(rq.priceCents());
        }

        if (rq.seatId() != null) {
            Seat seat = findSeat(rq.seatId());
            booking.setSeat(seat);
        }

        return mapper.apply(booking);
    }

    public void removeBooking(UUID id) {
        Booking booking = repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Booking not found " + id));
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            throw new IllegalStateTransitionException("Cannot remove already 'CONFIRMED' booking");
        }
        seatLockService.releaseLock(booking.getSeat().getId(), booking.getEvent().getId(), booking.getUser().getId());
        repo.delete(booking);
    }

    public UserSummaryDTO getUser(UUID bookingId) {
        return repo.findUserByBookingId(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found " + bookingId));
    }

    public EventSummaryDTO getEvent(UUID bookingId) {
        return repo.findEventByBookingId(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found " + bookingId));
    }

    public SeatSummaryDTO getSeat(UUID bookingId) {
        return repo.findSeatByBookingId(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found " + bookingId));
    }

    public PaymentSummaryDTO getPayment(UUID bookingId) {
        return paymentRepo.findPaymentByBookingId(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("This booking has not been paid yet " + bookingId));
    }

    @Transactional
    public void handleSuccessPayment(String bookingId, Session session) {
        Booking booking = repo.findById(UUID.fromString(bookingId))
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found " + bookingId));

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            // Handle booking bought by someone else during TTL and Stripe Session
            // expiration
            PaymentSummaryDTO payment = getPayment(booking.getId());
            if (payment.stripePaymentIntentId().equals(session.getPaymentIntent())) {
                // Log info here
                logger.info("Booking already paid, no-op...");
            } else {
                // Initiate refund
                stripeService.initiateRefund(bookingId, session);
                Payment paymentObject = paymentRepo.findById(payment.id()).get();
                paymentObject.setStatus(PaymentStatus.REFUNDED);
                paymentRepo.save(paymentObject);
            }

        } else { // Assumption: must be BookingStatus.PENDING
            booking.setStatus(BookingStatus.CONFIRMED);
            Payment payment = new Payment(session.getPaymentIntent(), session.getAmountSubtotal(),
                    session.getCurrency(), PaymentStatus.SUCCEEDED, booking);
            paymentRepo.save(payment);
        }
    }

    @Transactional
    public void handleExpiredPayment(String bookingId) {
        Booking booking = repo.findById(UUID.fromString(bookingId))
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found " + bookingId));

        booking.setStatus(BookingStatus.EXPIRED);
        seatLockService.releaseLock(booking.getSeat().getId(), booking.getEvent().getId(), booking.getUser().getId());
    }
}
