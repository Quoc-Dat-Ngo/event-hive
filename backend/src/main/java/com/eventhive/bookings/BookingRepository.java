package com.eventhive.bookings;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.eventhive.venues.EventSummaryDTO;
import com.eventhive.venues.SeatSummaryDTO;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    @Query("""
            SELECT NEW com.eventhive.bookings.UserSummaryDTO(u.firstName, u.lastName, u.email)
            FROM Booking b
            JOIN b.user u
            WHERE b.id = ?1
                """)
    Optional<UserSummaryDTO> findUserByBookingId(UUID bookingId);

    @Query("""
            SELECT NEW com.eventhive.venues.EventSummaryDTO(e.title, e.purpose, e.startsAt, e.endsAt, e.performer, e.status)
            FROM Booking b
            JOIN b.event e
            WHERE b.id = ?1
                """)
    Optional<EventSummaryDTO> findEventByBookingId(UUID bookingId);

    @Query("""
            SELECT NEW com.eventhive.venues.SeatSummaryDTO(s.seatRow, s.number)
            FROM Booking b
            JOIN b.seat s
            WHERE b.id = ?1
                """)
    Optional<SeatSummaryDTO> findSeatByBookingId(UUID bookingId);

    @Query("""
            SELECT NEW com.eventhive.bookings.BookingSummaryDTO(b.id, b.priceCents, b.status, b.user.id, b.event.id, b.seat.id)
            FROM Booking b
            WHERE b.event.id = ?1
                """)
    List<BookingSummaryDTO> findAllBookingsByEventId(UUID eventId);

    @Query("""
            SELECT NEW com.eventhive.bookings.BookingSummaryDTO(b.id, b.priceCents, b.status, b.user.id, b.event.id, b.seat.id)
            FROM Booking b
            WHERE b.user.id = ?1
                """)
    List<BookingSummaryDTO> findAllBookingsByUserId(UUID userId);
}
