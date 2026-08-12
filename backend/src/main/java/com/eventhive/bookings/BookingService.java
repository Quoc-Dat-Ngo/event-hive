package com.eventhive.bookings;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eventhive.events.Event;
import com.eventhive.events.EventRepository;
import com.eventhive.exception.ResourceNotFoundException;
import com.eventhive.payments.PaymentRepository;
import com.eventhive.payments.PaymentSummaryDTO;
import com.eventhive.seats.Seat;
import com.eventhive.seats.SeatRepository;
import com.eventhive.users.User;
import com.eventhive.users.UserRepository;
import com.eventhive.venues.EventSummaryDTO;
import com.eventhive.venues.SeatSummaryDTO;

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

    public BookingDTO addBooking(BookingRegistrationRequest rq, UUID verifiedUserId) {
        User user = userRepo.findById(verifiedUserId).orElseThrow(
                () -> new ResourceNotFoundException("User associted with this booking not found " + verifiedUserId));
        Event event = eventRepo.findById(rq.eventId()).orElseThrow(
                () -> new ResourceNotFoundException("Event associted with this booking not found " + rq.eventId()));
        Seat seat = findSeat(rq.seatId());

        Booking booking = new Booking(rq.priceCents(), rq.status(), user, event, seat);

        repo.save(booking);

        return mapper.apply(booking);
    }

    @Transactional
    public BookingDTO updateBooking(UUID id, BookingUpdateRequest rq) {
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

    public List<PaymentSummaryDTO> getAllPayments(UUID bookingId) {
        return paymentRepo.findAllPaymentsByBookingId(bookingId);
    }
}
