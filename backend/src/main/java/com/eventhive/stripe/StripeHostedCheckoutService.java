package com.eventhive.stripe;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;

import com.eventhive.bookings.Booking;
import com.eventhive.bookings.BookingRepository;
import com.eventhive.exception.PaymentProcessingException;
import com.eventhive.exception.PaymentRequiredException;
import com.eventhive.redis.SeatLockService;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StripeHostedCheckoutService {
	private final SeatLockService seatLockService;
	private final BookingRepository bookingRepository;

	public Session checkout(Booking booking) {
		SessionCreateParams params = SessionCreateParams.builder()
				.setMode(SessionCreateParams.Mode.PAYMENT)
				.setSuccessUrl("http://localhost:8181/success.html") // UPDATE LATER UPON DESIGNING UI
				.setCancelUrl("http://localhost:8181/cancel.html") // UPDATE LATER
				// expires_at is a Unix timestamp; Stripe requires 30 min to 24 h from now
				.setExpiresAt(Instant.now().plus(Duration.ofMinutes(31)).getEpochSecond())
				.addLineItem(
						SessionCreateParams.LineItem.builder()
								.setQuantity(1L)
								.setPriceData(
										SessionCreateParams.LineItem.PriceData
												.builder()
												.setCurrency("aud")
												.setUnitAmount((long) booking
														.getPriceCents())
												.setProductData(
														SessionCreateParams.LineItem.PriceData.ProductData
																.builder()
																.setName(booking.getEvent()
																		.getTitle()
																		+ " ticket")
																.build())
												.build())
								.build())
				.putMetadata("bookingId", booking.getId().toString())
				.setIntegrationIdentifier("hosted_web_" + booking.getId().toString())
				.build();

		try {
			Session session = Session.create(params,
					RequestOptions.builder()
							.setIdempotencyKey("idem_" + booking.getId().toString())
							.build());
			return session;
		} catch (CardException e) {
			seatLockService.releaseLock(booking.getSeat().getId(), booking.getEvent().getId(),
					booking.getUser().getId());
			throw new PaymentRequiredException("Given card failed to make payment. Please retry", e);
		} catch (StripeException e) {
			seatLockService.releaseLock(booking.getSeat().getId(), booking.getEvent().getId(),
					booking.getUser().getId());
			bookingRepository.delete(booking);
			throw new PaymentProcessingException("Stripe checkout failed for booking " + booking.getId(),
					e);
		}
	}

}
