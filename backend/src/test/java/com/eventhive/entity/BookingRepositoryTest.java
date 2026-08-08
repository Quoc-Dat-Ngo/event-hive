package com.eventhive.entity;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.eventhive.AbstractRepositoryTest;
import com.eventhive.bookings.Booking;
import com.eventhive.bookings.BookingRepository;
import com.eventhive.bookings.BookingStatus;
import com.eventhive.bookings.BookingSummaryDTO;
import com.eventhive.bookings.UserSummaryDTO;
import com.eventhive.events.Event;
import com.eventhive.events.EventRepository;
import com.eventhive.events.EventStatus;
import com.eventhive.seats.Seat;
import com.eventhive.seats.SeatRepository;
import com.eventhive.users.AuthProvider;
import com.eventhive.users.User;
import com.eventhive.users.UserRepository;
import com.eventhive.users.UserRole;
import com.eventhive.venues.EventSummaryDTO;
import com.eventhive.venues.SeatSummaryDTO;
import com.eventhive.venues.Venue;
import com.eventhive.venues.VenueRepository;

public class BookingRepositoryTest extends AbstractRepositoryTest {
    @Autowired
    BookingRepository bookingRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    SeatRepository seatRepository;

    @Autowired
    VenueRepository venueRepository;

    @Autowired
    EventRepository eventRepository;

    private User u;
    private Event e;
    private Seat s;
    private Venue v;

    @BeforeEach
    public void setUpNewBooking() {
        v = venueRepository.save(new Venue("Accor Stadium", 100000, "Sydney"));
        s = seatRepository.save(new Seat("A", 1, v));
        e = eventRepository
                .save(new Event("Euniverse", "Park Eun Bin fan meeting", Instant.parse("2026-08-10T14:30:00+10:00"),
                        Instant.parse("2026-09-10T14:30:00+10:00"), "Park Eun Bin", EventStatus.PUBLISHED, v));

        u = userRepository.save(new User("Kevin", "Ngo", "quocdat@gmail", "dat124", AuthProvider.LOCAL, UserRole.USER));

    }

    @Test
    public void shouldReturnAssociatedUser() {
        Booking booking = bookingRepository.save(new Booking(40000, BookingStatus.CONFIRMED, u, e, s));
        Optional<UserSummaryDTO> summary = bookingRepository.findUserByBookingId(booking.getId());
        assertThat(summary).hasValueSatisfying(dto -> {
            assertThat(dto.firstName()).isEqualTo("Kevin");
            assertThat(dto.lastName()).isEqualTo("Ngo");
            assertThat(dto.email()).isEqualTo("quocdat@gmail");
        });
    }

    @Test
    public void shouldReturnAssociatedEvent() {
        Booking booking = bookingRepository.save(new Booking(40000, BookingStatus.CONFIRMED, u, e, s));
        Optional<EventSummaryDTO> summary = bookingRepository.findEventByBookingId(booking.getId());
        assertThat(summary).hasValueSatisfying(dto -> {
            assertThat(dto.title()).isEqualTo("Euniverse");
            assertThat(dto.purpose()).isEqualTo("Park Eun Bin fan meeting");
            assertThat(dto.startsAt()).isEqualTo(e.getStartsAt());
            assertThat(dto.endsAt()).isEqualTo(e.getEndsAt());
            assertThat(dto.performer()).isEqualTo("Park Eun Bin");
            assertThat(dto.status()).isEqualTo(EventStatus.PUBLISHED);
        });
    }

    @Test
    public void shouldReturnAssociatedSeat() {
        Booking booking = bookingRepository.save(new Booking(40000, BookingStatus.CONFIRMED, u, e, s));
        Optional<SeatSummaryDTO> summary = bookingRepository.findSeatByBookingId(booking.getId());
        assertThat(summary).hasValueSatisfying(dto -> {
            assertThat(dto.seatRow()).isEqualTo("A");
            assertThat(dto.number()).isEqualTo(1);
        });
    }

    @Test
    public void shouldReturnAllBookingByUser() {
        Booking booking = bookingRepository.save(new Booking(40000, BookingStatus.CONFIRMED, u, e, s));

        Seat s2 = seatRepository.save(new Seat("B", 2, v));
        Booking booking2 = bookingRepository.save(new Booking(15555, BookingStatus.CANCELLED, u, e, s2));

        Seat s3 = seatRepository.save(new Seat("D", 10, v));
        Booking booking3 = bookingRepository.save(new Booking(20880, BookingStatus.PENDING, u, e, s3));

        List<BookingSummaryDTO> summary = bookingRepository.findAllBookingsByUserId(u.getId());

        assertThat(summary)
                .extracting("bookingId", "priceCents", "status", "userId", "eventId", "seatId")
                .contains(
                        tuple(booking.getId(), 40000, BookingStatus.CONFIRMED, u.getId(), e.getId(), s.getId()),
                        tuple(booking2.getId(), 15555, BookingStatus.CANCELLED, u.getId(), e.getId(), s2.getId()),
                        tuple(booking3.getId(), 20880, BookingStatus.PENDING, u.getId(), e.getId(), s3.getId()));
    }

    @Test
    public void shouldReturnAllBookingByEvent() {
        Booking booking = bookingRepository.save(new Booking(40000, BookingStatus.CONFIRMED, u, e, s));

        Seat s2 = seatRepository.save(new Seat("B", 2, v));
        Booking booking2 = bookingRepository.save(new Booking(15555, BookingStatus.CANCELLED, u, e, s2));

        Seat s3 = seatRepository.save(new Seat("D", 10, v));
        Booking booking3 = bookingRepository.save(new Booking(20880, BookingStatus.PENDING, u, e, s3));
        List<BookingSummaryDTO> summary = bookingRepository.findAllBookingsByEventId(e.getId());

        assertThat(summary)
                .extracting("bookingId", "priceCents", "status", "userId", "eventId", "seatId")
                .contains(
                        tuple(booking.getId(), 40000, BookingStatus.CONFIRMED, u.getId(), e.getId(), s.getId()),
                        tuple(booking2.getId(), 15555, BookingStatus.CANCELLED, u.getId(), e.getId(), s2.getId()),
                        tuple(booking3.getId(), 20880, BookingStatus.PENDING, u.getId(), e.getId(), s3.getId()));
    }

}
