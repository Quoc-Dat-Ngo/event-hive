package com.eventhive.stripe;

import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
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

	@Value("${eventhive.frontend-url}")
	private String frontendUrl;

	public Session checkout(Booking booking) {
		SessionCreateParams params = SessionCreateParams.builder()
				.setMode(SessionCreateParams.Mode.PAYMENT)
				.setSuccessUrl(frontendUrl + "/?checkout=success&bookingId=" + booking.getId())
				.setCancelUrl(frontendUrl + "/?checkout=cancelled&bookingId=" + booking.getId())
				// expires_at is a Unix timestamp; Stripe requires 30 min to 24 h from now
				.setExpiresAt(Instant.now().plus(SeatLockService.SEAT_HOLD).getEpochSecond())
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

	/** The session's payment URL while it can still be paid, otherwise empty. */
	public Optional<String> findOpenCheckoutUrl(String sessionId) {
		try {
			Session session = Session.retrieve(sessionId);
			return "open".equals(session.getStatus()) ? Optional.ofNullable(session.getUrl()) : Optional.empty();
		} catch (StripeException e) {
			throw new PaymentProcessingException("Could not retrieve checkout session " + sessionId, e);
		}
	}

	/** Stops an open session from being paid; a no-op if it is already complete or expired. */
	public void expireSession(String sessionId) {
		try {
			Session session = Session.retrieve(sessionId);
			if ("open".equals(session.getStatus())) {
				session.expire();
			}
		} catch (StripeException e) {
			throw new PaymentProcessingException("Could not expire checkout session " + sessionId, e);
		}
	}
}
