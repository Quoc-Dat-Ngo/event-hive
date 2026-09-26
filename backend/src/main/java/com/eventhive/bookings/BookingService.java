package com.eventhive.bookings;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eventhive.events.Event;
import com.eventhive.events.EventRepository;
import com.eventhive.exception.DuplicateResourceException;
import com.eventhive.exception.IllegalStateTransitionException;
import com.eventhive.exception.RequestValidationException;
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
import com.eventhive.tiers.PriceTier;
import com.eventhive.tiers.PriceTierSeatRepository;
import com.eventhive.users.User;
import com.eventhive.users.UserRepository;
import com.eventhive.venues.EventSummaryDTO;
import com.eventhive.venues.SeatSummaryDTO;
import com.stripe.model.checkout.Session;
import com.stripe.param.RefundCreateParams;

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
    private final PriceTierSeatRepository tierSeatRepo;

    private static final Logger logger = LoggerFactory.getLogger(BookingService.class);

    // Statuses that occupy a seat (mirrors the V5 partial unique index)
    private static final Set<BookingStatus> ACTIVE_STATUSES = EnumSet.of(BookingStatus.PENDING,
            BookingStatus.CONFIRMED);

    // Customers may cancel only up to this long before the event starts; admins at any time
    public static final Duration CANCELLATION_CUTOFF = Duration.ofHours(48);

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
        PriceTier tier = tierSeatRepo.findTierForSeat(rq.eventId(), rq.seatId()).orElseThrow(
                () -> new RequestValidationException("Seat " + rq.seatId() + " is not on sale for this event"));

        Optional<Booking> active = repo.findFirstByEventIdAndSeatIdAndStatusIn(rq.eventId(), rq.seatId(),
                ACTIVE_STATUSES);
        if (active.isPresent()) {
            Booking existing = active.get();
            if (existing.getStatus() == BookingStatus.CONFIRMED) {
                throw new DuplicateResourceException("Seat has already been booked for this event " + rq.seatId());
            }
            if (isWithinCheckoutWindow(existing)) {
                if (existing.getUser().getId().equals(verifiedUserId)) {
                    return resumeCheckout(existing);
                }
                throw new SeatAlreadyLockedException("Seat is currently reserved by another customer " + rq.seatId());
            }
            // The expiry webhook never arrived; free the seat ourselves
            expireStaleBooking(existing);
        }

        if (!seatLockService.tryLock(rq.seatId(), rq.eventId(), verifiedUserId)) {
            throw new SeatAlreadyLockedException("Seat is currently reserved by another customer " + rq.seatId());
        }

        try {
            Booking booking = new Booking(
                    tier.getPriceCents(),
                    BookingStatus.PENDING,
                    user,
                    event,
                    seat);

            repo.save(booking);

            // Handle payment here
            Session session = checkoutService.checkout(booking);
            booking.setCheckoutSessionId(session.getId());
            repo.save(booking);

            return new BookingRegistrationResponse(mapper.apply(booking), session.getUrl(), false);
        } catch (DataIntegrityViolationException ex) {
            // Lost a race to another request between the active-booking check and the insert
            seatLockService.releaseLock(rq.seatId(), rq.eventId(), verifiedUserId);
            throw new SeatAlreadyLockedException("Seat is currently reserved by another customer " + rq.seatId());
        } catch (RuntimeException ex) {
            seatLockService.releaseLock(rq.seatId(), rq.eventId(), verifiedUserId);
            throw ex;
        }
    }

    private boolean isWithinCheckoutWindow(Booking booking) {
        return booking.getCreatedAt().plus(SeatLockService.SEAT_HOLD).isAfter(Instant.now());
    }

    private BookingRegistrationResponse resumeCheckout(Booking booking) {
        String url = booking.getCheckoutSessionId() == null
                ? null
                : checkoutService.findOpenCheckoutUrl(booking.getCheckoutSessionId()).orElse(null);
        if (url == null) {
            // Session was just paid or expired; the webhook will settle the booking shortly
            throw new IllegalStateTransitionException(
                    "Checkout for booking " + booking.getId() + " is no longer open, please check its status shortly");
        }
        return new BookingRegistrationResponse(mapper.apply(booking), url, true);
    }

    private void expireStaleBooking(Booking booking) {
        logger.warn("Booking {} is still PENDING after the checkout window; expiring it", booking.getId());
        if (booking.getCheckoutSessionId() != null) {
            // Best effort: if a payment still slips through, handleSuccessPayment refunds it
            try {
                checkoutService.expireSession(booking.getCheckoutSessionId());
            } catch (RuntimeException ex) {
                logger.warn("Could not expire checkout session for booking {}", booking.getId(), ex);
            }
        }
        booking.setStatus(BookingStatus.EXPIRED);
        repo.save(booking);
        releaseSeat(booking);
    }

    private void releaseSeat(Booking booking) {
        seatLockService.releaseLock(booking.getSeat().getId(), booking.getEvent().getId(), booking.getUser().getId());
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

        if (rq.status() == BookingStatus.CANCELLED && ACTIVE_STATUSES.contains(booking.getStatus())) {
            // Same side effects as a customer cancellation (expire checkout / refund)
            cancel(booking);
        } else if (rq.status() != null) {
            booking.setStatus(rq.status());
        }

        return mapper.apply(booking);
    }

    @Transactional
    public BookingDTO cancelBooking(UUID id, boolean requestedByAdmin) {
        Booking booking = repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Booking not found " + id));
        if (!ACTIVE_STATUSES.contains(booking.getStatus())) {
            throw new IllegalStateTransitionException(
                    "Cannot cancel a booking that is already '" + booking.getStatus() + "'");
        }
        Instant cutoff = booking.getEvent().getStartsAt().minus(CANCELLATION_CUTOFF);
        if (!requestedByAdmin && !Instant.now().isBefore(cutoff)) {
            throw new IllegalStateTransitionException(
                    "Bookings can only be cancelled at least 48 hours before the event starts. "
                            + "Please contact support for help.");
        }
        cancel(booking);
        return mapper.apply(booking);
    }

    private void cancel(Booking booking) {
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            Payment payment = paymentRepo.findByBookingId(booking.getId()).orElseThrow(
                    () -> new IllegalStateTransitionException("Confirmed booking has no payment " + booking.getId()));
            payment.setStatus(PaymentStatus.REFUNDED);
            payment.setRefundedAt(Instant.now());
            booking.setStatus(BookingStatus.CANCELLED);
            // Stripe last: a failed refund throws and rolls the status changes back
            stripeService.initiateRefund(booking.getId().toString(), payment.getStripePaymentIntentId(),
                    RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER, "booking_cancelled");
        } else {
            if (booking.getCheckoutSessionId() != null) {
                // If the customer paid just before this, the success webhook refunds it
                checkoutService.expireSession(booking.getCheckoutSessionId());
            }
            booking.setStatus(BookingStatus.CANCELLED);
            releaseSeat(booking);
        }
    }

    public void removeBooking(UUID id) {
        Booking booking = repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Booking not found " + id));
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            throw new IllegalStateTransitionException("Cannot remove already 'CONFIRMED' booking");
        }
        releaseSeat(booking);
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
        String paymentIntentId = session.getPaymentIntent();
        Optional<Payment> existingPayment = paymentRepo.findByBookingId(booking.getId());

        if (existingPayment.isPresent() && existingPayment.get().getStripePaymentIntentId().equals(paymentIntentId)) {
            logger.info("Payment {} for booking {} already recorded, no-op", paymentIntentId, bookingId);
            return;
        }

        if (booking.getStatus() == BookingStatus.PENDING) {
            booking.setStatus(BookingStatus.CONFIRMED);
            paymentRepo.save(new Payment(paymentIntentId, session.getAmountSubtotal(),
                    session.getCurrency(), PaymentStatus.SUCCEEDED, booking));
            return;
        }

        // Paid for a booking that no longer holds the seat (expired/cancelled while the
        // session was still open), or a second charge for an already-paid booking
        logger.warn("Refunding payment {} for booking {} in status {}", paymentIntentId, bookingId,
                booking.getStatus());
        if (existingPayment.isEmpty()) {
            // One payment row per booking (V6), so only record it when none exists yet
            Payment refunded = new Payment(paymentIntentId, session.getAmountSubtotal(),
                    session.getCurrency(), PaymentStatus.REFUNDED, booking);
            refunded.setRefundedAt(Instant.now());
            paymentRepo.save(refunded);
        }
        stripeService.initiateRefund(bookingId, paymentIntentId, RefundCreateParams.Reason.DUPLICATE,
                "booking_not_payable_" + booking.getStatus().name().toLowerCase());
    }

    @Transactional
    public void handleExpiredPayment(String bookingId) {
        Booking booking = repo.findById(UUID.fromString(bookingId))
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found " + bookingId));

        if (booking.getStatus() != BookingStatus.PENDING) {
            // Duplicate delivery, or the booking was already cancelled/expired/confirmed;
            // acknowledge so Stripe stops retrying
            logger.info("Ignoring checkout.session.expired for booking {} in status {}", bookingId,
                    booking.getStatus());
            return;
        }

        booking.setStatus(BookingStatus.EXPIRED);
        releaseSeat(booking);
    }
}
