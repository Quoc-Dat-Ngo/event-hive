package com.eventhive.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.eventhive.AbstractRepositoryTest;
import com.eventhive.bookings.Booking;
import com.eventhive.bookings.BookingRepository;
import com.eventhive.bookings.BookingStatus;
import com.eventhive.bookings.BookingSummaryDTO;
import com.eventhive.events.Event;
import com.eventhive.events.EventRepository;
import com.eventhive.events.EventStatus;
import com.eventhive.payments.Payment;
import com.eventhive.payments.PaymentRepository;
import com.eventhive.payments.PaymentStatus;
import com.eventhive.payments.PaymentSummaryDTO;
import com.eventhive.seats.Seat;
import com.eventhive.seats.SeatRepository;
import com.eventhive.users.AuthProvider;
import com.eventhive.users.User;
import com.eventhive.users.UserRepository;
import com.eventhive.users.UserRole;
import com.eventhive.venues.Venue;
import com.eventhive.venues.VenueRepository;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class PaymentRepositoryTest extends AbstractRepositoryTest {
	@Autowired
	PaymentRepository paymentRepository;

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

	private Booking booking;
	private User u;
	private Event e;
	private Seat s;

	@BeforeEach
	public void setUpNewBooking() {
		Venue v = venueRepository.save(new Venue("Accor Stadium", 100000, "Sydney"));
		s = seatRepository.save(new Seat("A", 1, v));
		e = eventRepository
				.save(new Event("Euniverse", "Park Eun Bin fan meeting",
						Instant.parse("2026-08-10T14:30:00+10:00"),
						Instant.parse("2026-09-10T14:30:00+10:00"), "Park Eun Bin",
						EventStatus.PUBLISHED, v));

		u = userRepository.save(
				new User("Kevin", "Ngo", "quocdat@gmail", "dat124", AuthProvider.LOCAL, UserRole.USER));

		booking = bookingRepository.save(new Booking(40000, BookingStatus.CONFIRMED, u, e, s));
	}

	@Test
	public void shouldReturnAssociatedBooking() {
		Payment p = paymentRepository
				.save(new Payment("placeholder_id", 40000, "AUD", PaymentStatus.SUCCEEDED, booking));

		Optional<BookingSummaryDTO> summary = paymentRepository.findBookingAssociatedWithPaymentId(p.getId());

		assertThat(summary).hasValueSatisfying(dto -> {
			assertThat(dto.bookingId()).isEqualTo(booking.getId());
			assertThat(dto.priceCents()).isEqualTo(40000);
			assertThat(dto.status()).isEqualTo(BookingStatus.CONFIRMED);
			assertThat(dto.userId()).isEqualTo(u.getId());
			assertThat(dto.eventId()).isEqualTo(e.getId());
			assertThat(dto.seatId()).isEqualTo(s.getId());
		});
	}

	@Test
	public void shoulReturnAllAssociatedPayments() {
		Payment payment1 = paymentRepository
				.save(new Payment("placeholder_id", 40000, "AUD", PaymentStatus.SUCCEEDED, booking));
		Payment payment2 = paymentRepository
				.save(new Payment("placeholder_id", 25050, "AUD", PaymentStatus.FAILED, booking));
		Payment payment3 = paymentRepository
				.save(new Payment("placeholder_id", 18010, "AUD", PaymentStatus.REFUNDED, booking));

		List<PaymentSummaryDTO> summary = paymentRepository.findAllPaymentsByBookingId(booking.getId());

		assertThat(summary)
				.extracting("id", "stripePaymentIntentId", "amountCents", "currency", "status",
						"purchasedAt",
						"refundedAt")
				.contains(
						tuple(payment1.getId(), "placeholder_id", 40000, "AUD",
								PaymentStatus.SUCCEEDED,
								payment1.getPurchasedAt(), null),
						tuple(payment2.getId(), "placeholder_id", 25050, "AUD",
								PaymentStatus.FAILED,
								payment2.getPurchasedAt(), null),
						tuple(payment3.getId(), "placeholder_id", 18010, "AUD",
								PaymentStatus.REFUNDED,
								payment3.getPurchasedAt(), null));
	}
}
