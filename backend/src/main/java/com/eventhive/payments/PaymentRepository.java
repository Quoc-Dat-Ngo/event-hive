package com.eventhive.payments;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.eventhive.bookings.BookingSummaryDTO;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

	@Query("""
			SELECT NEW com.eventhive.bookings.BookingSummaryDTO(b.id, b.priceCents, b.status, b.user.id, b.event.id, b.seat.id)
			FROM Payment p
			JOIN p.booking b
			WHERE p.id = ?1
			""")
	Optional<BookingSummaryDTO> findBookingAssociatedWithPaymentId(UUID paymentId);

	@Query("""
			SELECT NEW com.eventhive.payments.PaymentSummaryDTO(p.id, p.stripePaymentIntentId, p.amountCents, p.currency, p.status, p.purchasedAt, p.refundedAt)
			FROM Payment p
			WHERE p.booking.id = ?1
			""")
	List<PaymentSummaryDTO> findAllPaymentsByBookingId(UUID bookingId);

	@Query("""
			SELECT u.id
			FROM Payment p
			JOIN p.booking b
			JOIN b.user u
			WHERE p.id = ?1
			""")
	Optional<UUID> getBookingUserId(UUID paymentId);
}
