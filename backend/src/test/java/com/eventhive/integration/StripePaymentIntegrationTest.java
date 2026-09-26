package com.eventhive.integration;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.eventhive.AbstractWebIntegrationTest;
import com.eventhive.bookings.Booking;
import com.eventhive.bookings.BookingRepository;
import com.eventhive.bookings.BookingStatus;
import com.eventhive.bookings.BookingSummaryDTO;
import com.eventhive.payments.Payment;
import com.eventhive.payments.PaymentRepository;
import com.eventhive.payments.PaymentStatus;
import com.eventhive.stripe.StripeHostedCheckoutService;
import com.eventhive.stripe.StripeService;
import com.eventhive.users.AuthProvider;
import com.eventhive.users.User;
import com.eventhive.users.UserRepository;
import com.eventhive.users.UserRole;
import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.RefundCreateParams;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Exercises the booking-to-payment flow end to end: seat-lock-guarded booking creation,
 * a mocked Stripe hosted checkout, and a real (signed) Stripe webhook delivery against
 * {@code WebhookController} - the same path {@link BookingService#handleSuccessPayment}
 * runs in production.
 */
@AutoConfigureMockMvc
public class StripePaymentIntegrationTest extends AbstractWebIntegrationTest {
	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private BookingRepository bookingRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@MockitoBean
	private StripeHostedCheckoutService checkoutService;

	@MockitoBean
	private StripeService stripeService;

	@Value("${stripe.webhook.signing}")
	private String webhookSigningSecret;

	private String seatId;
	private String eventId;
	private String venueId;

	private String extractIdFromMockMvc(String uri, String json) throws Exception {
		MvcResult result = mockMvc.perform(post(uri)
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andReturn();

		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asString();
	}

	@BeforeEach
	void setUpData() throws Exception {
		// Every booking is routed through the mocked hosted-checkout call; the payment
		// intent is derived deterministically from the booking id so tests can predict it
		// without capturing the mock's return value.
		when(checkoutService.checkout(any())).thenAnswer(invocation -> {
			Booking booking = invocation.getArgument(0);
			Session session = new Session();
			session.setId("cs_test_" + booking.getId());
			session.setPaymentIntent(paymentIntentFor(booking.getId()));
			session.setUrl("https://checkout.stripe.com/c/pay/cs_test_" + booking.getId());
			return session;
		});

		this.venueId = extractIdFromMockMvc("/api/v1/venues", """
				{
				    "name": "CBD",
				    "capacity": 10000,
				    "location": "Parramata, Sydney"
				}
				""");
		this.seatId = extractIdFromMockMvc("/api/v1/seats", String.format("""
				{
				    "seatRow": "AB",
				    "number": 2,
				    "venueId": "%s"
				}
				""", venueId));
		this.eventId = extractIdFromMockMvc("/api/v1/events", String.format("""
				{
				    "title": "Euniverse",
				    "purpose": "Park Eun Bin fan meeting",
				    "startsAt": "%s",
				    "endsAt": "%s",
				    "status": "PUBLISHED",
				    "venueId": "%s"
				}
				""", Instant.now().plus(7, ChronoUnit.DAYS), Instant.now().plus(8, ChronoUnit.DAYS), venueId));
		// Bookings are priced from the seat's tier, so the seat must belong to one
		extractIdFromMockMvc("/api/v1/events/" + eventId + "/tiers", """
				{
				    "name": "Front stalls",
				    "priceCents": 20000,
				    "seatRanges": [{ "rowFrom": "AB" }]
				}
				""");
	}

	private String paymentIntentFor(UUID bookingId) {
		return "pi_test_" + bookingId;
	}

	private User registerUser(String firstName, String email) {
		User user = new User(firstName, "Ngo", email, passwordEncoder.encode("pass123"), AuthProvider.LOCAL,
				UserRole.USER);
		userRepository.saveAndFlush(user);
		return user;
	}

	private String bookingRequestJson() {
		return String.format("""
				{
				    "priceCents": 20000,
				    "eventId": "%s",
				    "seatId": "%s"
				}
				""", eventId, seatId);
	}

	private MvcResult postBooking(User user) throws Exception {
		return mockMvc.perform(post("/api/v1/bookings")
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
						.jwt(builder -> builder.subject(user.getEmail())
								.claim("userId", user.getId().toString())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(bookingRequestJson()))
				.andReturn();
	}

	/** Builds a checkout.session.completed payload shaped like Stripe's real webhook body. */
	private String checkoutSessionCompletedPayload(String bookingId, String paymentIntentId,
			long amountSubtotalCents, String currency) {
		return checkoutSessionEventPayload("checkout.session.completed", "complete", "paid", bookingId,
				paymentIntentId, amountSubtotalCents, currency, Stripe.API_VERSION);
	}

	/** Builds a checkout.session.expired payload shaped like Stripe's real webhook body. */
	private String checkoutSessionExpiredPayload(String bookingId, String paymentIntentId,
			long amountSubtotalCents, String currency) {
		return checkoutSessionEventPayload("checkout.session.expired", "expired", "unpaid", bookingId,
				paymentIntentId, amountSubtotalCents, currency, Stripe.API_VERSION);
	}

	private String checkoutSessionEventPayload(String eventType, String sessionStatus, String paymentStatus,
			String bookingId, String paymentIntentId, long amountSubtotalCents, String currency, String apiVersion) {
		return String.format("""
				{
				  "id": "evt_test_%s",
				  "object": "event",
				  "api_version": "%s",
				  "created": %d,
				  "type": "%s",
				  "livemode": false,
				  "pending_webhooks": 1,
				  "request": {"id": null, "idempotency_key": null},
				  "data": {
				    "object": {
				      "id": "cs_test_%s",
				      "object": "checkout.session",
				      "mode": "payment",
				      "status": "%s",
				      "payment_status": "%s",
				      "payment_intent": "%s",
				      "amount_subtotal": %d,
				      "amount_total": %d,
				      "currency": "%s",
				      "metadata": {"bookingId": "%s"}
				    }
				  }
				}
				""", UUID.randomUUID(), apiVersion, Instant.now().getEpochSecond(), eventType,
				UUID.randomUUID(), sessionStatus, paymentStatus, paymentIntentId, amountSubtotalCents,
				amountSubtotalCents, currency.toLowerCase(), bookingId);
	}

	private String signedWebhookHeader(String payload) throws Exception {
		return Webhook.Signature.generateSignatureHeader(payload, webhookSigningSecret);
	}

	private void deliverWebhook(String payload) throws Exception {
		mockMvc.perform(post("/api/v1/stripe/webhooks")
				.contentType(MediaType.APPLICATION_JSON)
				.header("Stripe-Signature", signedWebhookHeader(payload))
				.content(payload))
				.andExpect(status().isOk());
	}

	@Test
	void shouldConfirmExactlyOneBookingWhenTwoUsersRaceForSameSeat() throws Exception {
		User userA = registerUser("Kevin", "kevin@example.com");
		User userB = registerUser("Lucas", "lucas@example.com");

		ExecutorService executorService = Executors.newFixedThreadPool(2);
		CountDownLatch readyLatch = new CountDownLatch(2);
		CountDownLatch startLatch = new CountDownLatch(1);

		Callable<MvcResult> requestA = () -> {
			readyLatch.countDown();
			startLatch.await();
			return postBooking(userA);
		};
		Callable<MvcResult> requestB = () -> {
			readyLatch.countDown();
			startLatch.await();
			return postBooking(userB);
		};

		Future<MvcResult> resultA = executorService.submit(requestA);
		Future<MvcResult> resultB = executorService.submit(requestB);

		readyLatch.await();
		startLatch.countDown();

		MvcResult responseA = resultA.get();
		MvcResult responseB = resultB.get();
		executorService.shutdown();

		List<Integer> statuses = List.of(responseA.getResponse().getStatus(), responseB.getResponse().getStatus());
		assertThat(statuses).containsExactlyInAnyOrder(201, 409);

		MvcResult winningResponse = responseA.getResponse().getStatus() == 201 ? responseA : responseB;
		JsonNode winningBooking = objectMapper.readTree(winningResponse.getResponse().getContentAsString())
				.get("booking");
		String bookingId = winningBooking.get("id").asString();

		// Only one booking was ever created for this seat/event pair.
		List<BookingSummaryDTO> bookingsForEvent = bookingRepository.findAllBookingsByEventId(UUID.fromString(eventId));
		assertThat(bookingsForEvent).hasSize(1);
		assertThat(bookingsForEvent.get(0).status()).isEqualTo(BookingStatus.PENDING);

		// Stripe confirms payment for the winning booking via webhook.
		String payload = checkoutSessionCompletedPayload(bookingId, paymentIntentFor(UUID.fromString(bookingId)),
				20000, "AUD");
		deliverWebhook(payload);

		mockMvc.perform(get("/api/v1/bookings/" + bookingId)
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CONFIRMED"));

		List<BookingSummaryDTO> confirmedBookings = bookingRepository.findAllBookingsByEventId(UUID.fromString(eventId));
		assertThat(confirmedBookings).hasSize(1);
		assertThat(confirmedBookings.get(0).status()).isEqualTo(BookingStatus.CONFIRMED);
		assertThat(paymentRepository.findAll()).hasSize(1);
	}

	@Test
	void shouldNotDoubleProcessRetriedWebhookEvent() throws Exception {
		User user = registerUser("Kevin", "kevin@example.com");

		MvcResult bookingResponse = postBooking(user);
		assertThat(bookingResponse.getResponse().getStatus()).isEqualTo(201);
		String bookingId = objectMapper.readTree(bookingResponse.getResponse().getContentAsString())
				.get("booking").get("id").asString();

		String paymentIntentId = paymentIntentFor(UUID.fromString(bookingId));
		String payload = checkoutSessionCompletedPayload(bookingId, paymentIntentId, 20000, "AUD");
		// Stripe signs the retried delivery identically to the original - same payload,
		// same signature - since it's the same event replayed, not a new one.
		String signatureHeader = signedWebhookHeader(payload);

		mockMvc.perform(post("/api/v1/stripe/webhooks")
				.contentType(MediaType.APPLICATION_JSON)
				.header("Stripe-Signature", signatureHeader)
				.content(payload))
				.andExpect(status().isOk());

		assertThat(paymentRepository.findAll()).hasSize(1);
		Payment firstPayment = paymentRepository.findAll().get(0);
		assertThat(firstPayment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
		assertThat(firstPayment.getStripePaymentIntentId()).isEqualTo(paymentIntentId);

		mockMvc.perform(get("/api/v1/bookings/" + bookingId)
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CONFIRMED"));

		// Stripe retries the exact same event (e.g. it never saw our 200 ack in time).
		mockMvc.perform(post("/api/v1/stripe/webhooks")
				.contentType(MediaType.APPLICATION_JSON)
				.header("Stripe-Signature", signatureHeader)
				.content(payload))
				.andExpect(status().isOk());

		// No second Payment/charge was recorded, and the existing one is untouched.
		List<Payment> paymentsAfterRetry = paymentRepository.findAll();
		assertThat(paymentsAfterRetry).hasSize(1);
		assertThat(paymentsAfterRetry.get(0).getId()).isEqualTo(firstPayment.getId());
		assertThat(paymentsAfterRetry.get(0).getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);

		mockMvc.perform(get("/api/v1/bookings/" + bookingId)
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CONFIRMED"));
	}

	@Test
	void shouldExpireBookingAndReleaseSeatLockWhenCheckoutSessionExpires() throws Exception {
		User user = registerUser("Kevin", "kevin@example.com");

		MvcResult bookingResponse = postBooking(user);
		assertThat(bookingResponse.getResponse().getStatus()).isEqualTo(201);
		String bookingId = objectMapper.readTree(bookingResponse.getResponse().getContentAsString())
				.get("booking").get("id").asString();

		String lockKey = "seat-lock:" + eventId + ":" + seatId;
		assertThat(redisTemplate.hasKey(lockKey)).isTrue();

		String payload = checkoutSessionExpiredPayload(bookingId, paymentIntentFor(UUID.fromString(bookingId)),
				20000, "AUD");
		deliverWebhook(payload);

		mockMvc.perform(get("/api/v1/bookings/" + bookingId)
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("EXPIRED"));

		// The seat lock is released so another customer can immediately rebook the seat.
		assertThat(redisTemplate.hasKey(lockKey)).isFalse();
		assertThat(paymentRepository.findAll()).isEmpty();
	}

	@Test
	void shouldNoOpOnRetriedPaymentIntentThenRefundOnConflictingPaymentIntent() throws Exception {
		User user = registerUser("Kevin", "kevin@example.com");
		String bookingId = createBooking(user);

		String originalPaymentIntent = paymentIntentFor(UUID.fromString(bookingId));
		deliverWebhook(checkoutSessionCompletedPayload(bookingId, originalPaymentIntent, 20000, "AUD"));

		assertThat(paymentRepository.findAll()).hasSize(1);
		Payment originalPayment = paymentRepository.findAll().get(0);
		assertThat(originalPayment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);

		// No-op branch: the same payment_intent arrives again (a webhook retry)
		deliverWebhook(checkoutSessionCompletedPayload(bookingId, originalPaymentIntent, 20000, "AUD"));

		assertThat(paymentRepository.findAll()).hasSize(1);
		verify(stripeService, never()).initiateRefund(any(), any(), any(), any());

		// Refund branch: a DIFFERENT payment_intent arrives for the already-paid booking
		String conflictingPaymentIntent = "pi_test_stale_" + UUID.randomUUID();
		deliverWebhook(checkoutSessionCompletedPayload(bookingId, conflictingPaymentIntent, 20000, "AUD"));

		// Only the extra charge is refunded; the original payment is untouched
		verify(stripeService).initiateRefund(eq(bookingId), eq(conflictingPaymentIntent),
				eq(RefundCreateParams.Reason.DUPLICATE), any());
		List<Payment> paymentsAfterRefund = paymentRepository.findAll();
		assertThat(paymentsAfterRefund).hasSize(1);
		assertThat(paymentsAfterRefund.get(0).getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
		assertThat(paymentsAfterRefund.get(0).getStripePaymentIntentId()).isEqualTo(originalPaymentIntent);
		assertBookingStatus(bookingId, "CONFIRMED");
	}

	@Test
	void shouldConfirmBookingWhenWebhookApiVersionDiffersFromSdk() throws Exception {
		User user = registerUser("Kevin", "kevin@example.com");

		MvcResult bookingResponse = postBooking(user);
		assertThat(bookingResponse.getResponse().getStatus()).isEqualTo(201);
		String bookingId = objectMapper.readTree(bookingResponse.getResponse().getContentAsString())
				.get("booking").get("id").asString();

		// `stripe listen` delivers events in the account's default API version, which
		// rarely matches the version pinned by stripe-java.
		String payload = checkoutSessionEventPayload("checkout.session.completed", "complete", "paid", bookingId,
				paymentIntentFor(UUID.fromString(bookingId)), 20000, "AUD", "2024-06-20");
		deliverWebhook(payload);

		mockMvc.perform(get("/api/v1/bookings/" + bookingId)
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CONFIRMED"));
		assertThat(paymentRepository.findAll()).hasSize(1);
	}

	private String createBooking(User user) throws Exception {
		MvcResult response = postBooking(user);
		assertThat(response.getResponse().getStatus()).isEqualTo(201);
		return objectMapper.readTree(response.getResponse().getContentAsString())
				.get("booking").get("id").asString();
	}

	private void assertBookingStatus(String bookingId, String status) throws Exception {
		mockMvc.perform(get("/api/v1/bookings/" + bookingId)
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value(status));
	}

	private MvcResult cancelBooking(User user, String bookingId) throws Exception {
		return mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/cancel")
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
						.jwt(builder -> builder.subject(user.getEmail())
								.claim("userId", user.getId().toString()))))
				.andReturn();
	}

	private String lockKey() {
		return "seat-lock:" + eventId + ":" + seatId;
	}

	@Test
	void shouldResumeOwnPendingBookingWithinCheckoutWindow() throws Exception {
		User user = registerUser("Kevin", "kevin@example.com");
		String bookingId = createBooking(user);
		when(checkoutService.findOpenCheckoutUrl("cs_test_" + bookingId))
				.thenReturn(Optional.of("https://checkout.stripe.com/c/pay/cs_test_" + bookingId));

		MvcResult resumed = postBooking(user);

		assertThat(resumed.getResponse().getStatus()).isEqualTo(200);
		JsonNode body = objectMapper.readTree(resumed.getResponse().getContentAsString());
		assertThat(body.get("booking").get("id").asString()).isEqualTo(bookingId);
		assertThat(body.get("resumed").asBoolean()).isTrue();
		assertThat(body.get("url").asString()).endsWith("cs_test_" + bookingId);
		assertThat(bookingRepository.findAll()).hasSize(1);
	}

	@Test
	void shouldRejectOtherCustomerWithClearMessageWhileSeatIsPending() throws Exception {
		User userA = registerUser("Kevin", "kevin@example.com");
		User userB = registerUser("Lucas", "lucas@example.com");
		createBooking(userA);

		// Even with the Redis lock gone, the PENDING booking still reserves the seat
		redisTemplate.delete(lockKey());
		MvcResult response = postBooking(userB);

		assertThat(response.getResponse().getStatus()).isEqualTo(409);
		assertThat(objectMapper.readTree(response.getResponse().getContentAsString()).get("message").asString())
				.isEqualTo("Seat is currently reserved by another customer " + seatId);
	}

	@Test
	void shouldRejectBookingForAlreadyConfirmedSeat() throws Exception {
		User userA = registerUser("Kevin", "kevin@example.com");
		User userB = registerUser("Lucas", "lucas@example.com");
		String bookingId = createBooking(userA);
		deliverWebhook(checkoutSessionCompletedPayload(bookingId, paymentIntentFor(UUID.fromString(bookingId)),
				20000, "AUD"));

		MvcResult response = postBooking(userB);

		assertThat(response.getResponse().getStatus()).isEqualTo(409);
		assertThat(objectMapper.readTree(response.getResponse().getContentAsString()).get("message").asString())
				.isEqualTo("Seat has already been booked for this event " + seatId);
	}

	@Test
	void shouldExpireStalePendingBookingAndLetAnotherCustomerBook() throws Exception {
		User userA = registerUser("Kevin", "kevin@example.com");
		User userB = registerUser("Lucas", "lucas@example.com");
		String staleBookingId = createBooking(userA);

		// Simulate a missed expiry webhook: the checkout window has passed
		jdbcTemplate.update("UPDATE bookings SET created_at = ? WHERE id = ?",
				Timestamp.from(Instant.now().minus(32, ChronoUnit.MINUTES)), UUID.fromString(staleBookingId));
		redisTemplate.delete(lockKey());

		String newBookingId = createBooking(userB);

		assertThat(newBookingId).isNotEqualTo(staleBookingId);
		assertBookingStatus(staleBookingId, "EXPIRED");
		assertBookingStatus(newBookingId, "PENDING");
		verify(checkoutService).expireSession("cs_test_" + staleBookingId);
	}

	@Test
	void shouldCancelPendingBookingAndReleaseSeat() throws Exception {
		User user = registerUser("Kevin", "kevin@example.com");
		String bookingId = createBooking(user);
		assertThat(redisTemplate.hasKey(lockKey())).isTrue();

		MvcResult response = cancelBooking(user, bookingId);

		assertThat(response.getResponse().getStatus()).isEqualTo(200);
		assertBookingStatus(bookingId, "CANCELLED");
		assertThat(redisTemplate.hasKey(lockKey())).isFalse();
		verify(checkoutService).expireSession("cs_test_" + bookingId);
		verify(stripeService, never()).initiateRefund(any(), any(), any(), any());

		// Stripe then reports the expired session; the cancelled booking is left alone
		deliverWebhook(checkoutSessionExpiredPayload(bookingId, paymentIntentFor(UUID.fromString(bookingId)),
				20000, "AUD"));
		assertBookingStatus(bookingId, "CANCELLED");
	}

	@Test
	void shouldRefundWhenCustomerCancelsConfirmedBooking() throws Exception {
		User user = registerUser("Kevin", "kevin@example.com");
		String bookingId = createBooking(user);
		String paymentIntentId = paymentIntentFor(UUID.fromString(bookingId));
		deliverWebhook(checkoutSessionCompletedPayload(bookingId, paymentIntentId, 20000, "AUD"));

		MvcResult response = cancelBooking(user, bookingId);

		assertThat(response.getResponse().getStatus()).isEqualTo(200);
		assertBookingStatus(bookingId, "CANCELLED");
		verify(stripeService).initiateRefund(eq(bookingId), eq(paymentIntentId),
				eq(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER), any());
		Payment payment = paymentRepository.findAll().get(0);
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
		assertThat(payment.getRefundedAt()).isNotNull();

		// A second cancel is rejected rather than refunding twice
		assertThat(cancelBooking(user, bookingId).getResponse().getStatus()).isEqualTo(409);
	}

	@Test
	void shouldForbidCancellingSomeoneElsesBooking() throws Exception {
		User owner = registerUser("Kevin", "kevin@example.com");
		User other = registerUser("Lucas", "lucas@example.com");
		String bookingId = createBooking(owner);

		assertThat(cancelBooking(other, bookingId).getResponse().getStatus()).isEqualTo(403);
		assertBookingStatus(bookingId, "PENDING");
	}

	@Test
	void shouldRefundPaymentThatArrivesForCancelledBooking() throws Exception {
		User user = registerUser("Kevin", "kevin@example.com");
		String bookingId = createBooking(user);
		cancelBooking(user, bookingId);

		// The customer completed payment just before the session was expired
		String paymentIntentId = paymentIntentFor(UUID.fromString(bookingId));
		String payload = checkoutSessionCompletedPayload(bookingId, paymentIntentId, 20000, "AUD");
		deliverWebhook(payload);

		assertBookingStatus(bookingId, "CANCELLED");
		verify(stripeService).initiateRefund(eq(bookingId), eq(paymentIntentId),
				eq(RefundCreateParams.Reason.DUPLICATE), any());
		Payment payment = paymentRepository.findAll().get(0);
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
		assertThat(payment.getStripePaymentIntentId()).isEqualTo(paymentIntentId);

		// A retried delivery is a no-op, not a second refund
		deliverWebhook(payload);
		verify(stripeService, times(1)).initiateRefund(any(), any(), any(), any());
	}

	@Test
	void shouldIgnoreExpiredWebhookForConfirmedBooking() throws Exception {
		User user = registerUser("Kevin", "kevin@example.com");
		String bookingId = createBooking(user);
		String paymentIntentId = paymentIntentFor(UUID.fromString(bookingId));
		deliverWebhook(checkoutSessionCompletedPayload(bookingId, paymentIntentId, 20000, "AUD"));

		deliverWebhook(checkoutSessionExpiredPayload(bookingId, paymentIntentId, 20000, "AUD"));

		assertBookingStatus(bookingId, "CONFIRMED");
	}

	private void moveEventStartTo(Instant startsAt) {
		jdbcTemplate.update("UPDATE events SET starts_at = ?, ends_at = ? WHERE id = ?",
				Timestamp.from(startsAt), Timestamp.from(startsAt.plus(2, ChronoUnit.HOURS)), UUID.fromString(eventId));
	}

	@Test
	void shouldRejectCustomerCancellationWithin48HoursOfEvent() throws Exception {
		User user = registerUser("Kevin", "kevin@example.com");
		String bookingId = createBooking(user);
		deliverWebhook(checkoutSessionCompletedPayload(bookingId, paymentIntentFor(UUID.fromString(bookingId)),
				20000, "AUD"));
		moveEventStartTo(Instant.now().plus(47, ChronoUnit.HOURS));

		MvcResult response = cancelBooking(user, bookingId);

		assertThat(response.getResponse().getStatus()).isEqualTo(409);
		assertThat(objectMapper.readTree(response.getResponse().getContentAsString()).get("message").asString())
				.startsWith("Bookings can only be cancelled at least 48 hours before the event starts");
		assertBookingStatus(bookingId, "CONFIRMED");
		verify(stripeService, never()).initiateRefund(any(), any(), any(), any());
	}

	@Test
	void shouldLetAdminCancelAndRefundWithin48HoursOfEvent() throws Exception {
		User user = registerUser("Kevin", "kevin@example.com");
		String bookingId = createBooking(user);
		String paymentIntentId = paymentIntentFor(UUID.fromString(bookingId));
		deliverWebhook(checkoutSessionCompletedPayload(bookingId, paymentIntentId, 20000, "AUD"));
		moveEventStartTo(Instant.now().plus(1, ChronoUnit.HOURS));

		mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/cancel")
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
						.jwt(builder -> builder.claim("userId", UUID.randomUUID().toString()))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));

		verify(stripeService).initiateRefund(eq(bookingId), eq(paymentIntentId),
				eq(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER), any());
		assertThat(paymentRepository.findAll().get(0).getStatus()).isEqualTo(PaymentStatus.REFUNDED);
	}
}
