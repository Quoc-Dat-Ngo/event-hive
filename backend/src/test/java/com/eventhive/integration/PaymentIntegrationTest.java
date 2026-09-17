package com.eventhive.integration;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.eventhive.AbstractWebIntegrationTest;
import com.eventhive.stripe.StripeHostedCheckoutService;
import com.eventhive.users.AuthProvider;
import com.eventhive.users.User;
import com.eventhive.users.UserRepository;
import com.eventhive.users.UserRole;
import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Payments are no longer created by a client-facing endpoint (see commit 3bc4059);
 * they're created as a side effect of a Stripe {@code checkout.session.completed}
 * webhook. These tests drive that flow by POSTing a signed webhook payload, the same
 * way Stripe would, rather than calling a (now removed) POST /api/v1/payments.
 */
@AutoConfigureMockMvc
public class PaymentIntegrationTest extends AbstractWebIntegrationTest {
	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private UserRepository userRepository;

	@MockitoBean
	private StripeHostedCheckoutService checkoutService;

	@Value("${stripe.webhook.signing}")
	private String webhookSigningSecret;

	private User testUser;

	@BeforeEach
	void setUpUser() {
		testUser = new User("Kevin", "Ngo", "kevin@example.com",
				passwordEncoder.encode("pass123"), AuthProvider.LOCAL, UserRole.USER);
		userRepository.saveAndFlush(testUser);

		Session fakeSession = new Session();
		fakeSession.setId("cs_test_" + UUID.randomUUID());
		fakeSession.setUrl("https://checkout.stripe.com/c/pay/cs_test_mock");
		fakeSession.setPaymentIntent("pi_test_" + UUID.randomUUID());
		when(checkoutService.checkout(any())).thenReturn(fakeSession);
	}

	private String extractIdFromMockMvc(String uri, String json) throws Exception {
		MvcResult result = mockMvc.perform(post(uri)
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andReturn();

		JsonNode rootNode = objectMapper.readTree(result.getResponse().getContentAsString());
		return rootNode.get("id").asString();
	}

	private String extractIdFromMockMvcWithJwtClaim(String uri, String json)
			throws Exception {
		MvcResult result = mockMvc.perform(post(uri)
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
						.jwt(builder -> builder.subject(testUser.getEmail())
								.claim("userId", testUser.getId().toString())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.booking.id").exists())
				.andReturn();

		JsonNode rootNode = objectMapper.readTree(result.getResponse().getContentAsString());
		return rootNode.get("booking").get("id").asString();
	}

	private String createVenue() throws Exception {
		return extractIdFromMockMvc("/api/v1/venues", """
				{
				    "name": "CBD",
				    "capacity": 10000,
				    "location": "Parramata, Sydney"
				}
				""");
	}

	private String createSeat(String venueId) throws Exception {
		return extractIdFromMockMvc("/api/v1/seats", String.format("""
				{
				    "seatRow": "AB",
				    "number": 2,
				    "venueId": "%s"
				}
				""", venueId));
	}

	private String createEvent(String venueId) throws Exception {
		Instant startsAt = Instant.now().plusSeconds(3600);
		Instant endsAt = Instant.now().plusSeconds(7200);
		return extractIdFromMockMvc("/api/v1/events", String.format("""
				{
				    "title": "Euniverse",
				    "purpose": "Park Eun Bin fan meeting",
				    "startsAt": "%s",
				    "endsAt": "%s",
				    "status": "PUBLISHED",
				    "venueId": "%s"
				}
				""", startsAt, endsAt, venueId));
	}

	private String createBooking(String eventId, String seatId) throws Exception {
		return extractIdFromMockMvcWithJwtClaim("/api/v1/bookings", String.format("""
				{
				    "priceCents": 20000,
				    "eventId": "%s",
				    "seatId": "%s"
				}
				""", eventId, seatId));
	}

	/**
	 * Builds a checkout.session.completed payload shaped like Stripe's real webhook body -
	 * only the fields BookingService/PaymentDTOMapper actually read are populated.
	 */
	private String checkoutSessionCompletedPayload(String bookingId, String paymentIntentId,
			long amountSubtotalCents, String currency) {
		return String.format("""
				{
				  "id": "evt_test_%s",
				  "object": "event",
				  "api_version": "%s",
				  "created": %d,
				  "type": "checkout.session.completed",
				  "livemode": false,
				  "pending_webhooks": 1,
				  "request": {"id": null, "idempotency_key": null},
				  "data": {
				    "object": {
				      "id": "cs_test_%s",
				      "object": "checkout.session",
				      "mode": "payment",
				      "status": "complete",
				      "payment_status": "paid",
				      "payment_intent": "%s",
				      "amount_subtotal": %d,
				      "amount_total": %d,
				      "currency": "%s",
				      "metadata": {"bookingId": "%s"}
				    }
				  }
				}
				""", UUID.randomUUID(), Stripe.API_VERSION, Instant.now().getEpochSecond(), UUID.randomUUID(),
				paymentIntentId, amountSubtotalCents, amountSubtotalCents, currency.toLowerCase(), bookingId);
	}

	private String signedWebhookHeader(String payload) throws Exception {
		return Webhook.Signature.generateSignatureHeader(payload, webhookSigningSecret);
	}

	/** Confirms payment for a booking the same way Stripe would, via the webhook endpoint. */
	private void confirmPaymentViaWebhook(String bookingId, String paymentIntentId,
			long amountSubtotalCents, String currency) throws Exception {
		String payload = checkoutSessionCompletedPayload(bookingId, paymentIntentId, amountSubtotalCents, currency);

		mockMvc.perform(post("/api/v1/stripe/webhooks")
				.contentType(MediaType.APPLICATION_JSON)
				.header("Stripe-Signature", signedWebhookHeader(payload))
				.content(payload))
				.andExpect(status().isOk());
	}

	private String getPaymentIdForBooking(String bookingId) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/bookings/" + bookingId + "/payments")
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
				.andExpect(status().isOk())
				.andReturn();

		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asString();
	}

	@Test
	void shouldRequireAuthenticationWhenAccessingPaymentWithoutJwt() throws Exception {
		String venueId = createVenue();
		String seatId = createSeat(venueId);
		String eventId = createEvent(venueId);
		String bookingId = createBooking(eventId, seatId);

		confirmPaymentViaWebhook(bookingId, "pi_" + UUID.randomUUID(), 20000, "AUD");
		String paymentId = getPaymentIdForBooking(bookingId);

		mockMvc.perform(get("/api/v1/payments/" + paymentId))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void shouldCreatePaymentAndReturnAssociatedBookingSummary() throws Exception {
		String venueId = createVenue();
		String seatId = createSeat(venueId);
		String eventId = createEvent(venueId);
		String bookingId = createBooking(eventId, seatId);

		confirmPaymentViaWebhook(bookingId, "pi_" + UUID.randomUUID(), 20000, "AUD");
		String paymentId = getPaymentIdForBooking(bookingId);

		mockMvc.perform(get("/api/v1/payments/" + paymentId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(paymentId))
				.andExpect(jsonPath("$.amountCents").value(20000))
				.andExpect(jsonPath("$.currency").value("aud"))
				.andExpect(jsonPath("$.status").value("SUCCEEDED"))
				.andExpect(jsonPath("$.bookingId").value(bookingId));

		mockMvc.perform(get("/api/v1/payments/" + paymentId + "/booking"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.bookingId").value(bookingId));
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void shouldUpdatePaymentStatusAndRefundedAt() throws Exception {
		String venueId = createVenue();
		String seatId = createSeat(venueId);
		String eventId = createEvent(venueId);
		String bookingId = createBooking(eventId, seatId);

		confirmPaymentViaWebhook(bookingId, "pi_" + UUID.randomUUID(), 20000, "AUD");
		String paymentId = getPaymentIdForBooking(bookingId);

		String updatePaymentJson = String.format("""
				{
				    "status": "REFUNDED",
				    "refundedAt": "%s"
				}
				""", Instant.now().toString());

		mockMvc.perform(put("/api/v1/payments/" + paymentId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(updatePaymentJson))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(paymentId))
				.andExpect(jsonPath("$.status").value("REFUNDED"))
				.andExpect(jsonPath("$.refundedAt").exists());
	}

	@Test
	void shouldFailWebhookProcessingWhenBookingDoesNotExist() throws Exception {
		String bookingId = UUID.randomUUID().toString();
		String payload = checkoutSessionCompletedPayload(bookingId, "pi_" + UUID.randomUUID(), 20000, "AUD");

		// Stripe webhooks have no client waiting on a response body/shape - a processing
		// failure just surfaces as a 500 so Stripe retries. No Payment should be persisted.
		mockMvc.perform(post("/api/v1/stripe/webhooks")
				.contentType(MediaType.APPLICATION_JSON)
				.header("Stripe-Signature", signedWebhookHeader(payload))
				.content(payload))
				.andExpect(status().isInternalServerError());
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void shouldForbidNonOwnerFromViewingSomeoneElsesPayment() throws Exception {
		User intruder = new User("intruder", "test", "intruder@test.com",
				passwordEncoder.encode("pass123"), AuthProvider.LOCAL, UserRole.USER);

		userRepository.saveAndFlush(intruder);

		String venueId = createVenue();
		String seatId = createSeat(venueId);
		String eventId = createEvent(venueId);
		String bookingId = createBooking(eventId, seatId);

		confirmPaymentViaWebhook(bookingId, "pi_" + UUID.randomUUID(), 20000, "AUD");
		String paymentId = getPaymentIdForBooking(bookingId);

		mockMvc.perform(get("/api/v1/payments/" + paymentId)
				.with(jwt()
						.authorities(new SimpleGrantedAuthority("ROLE_USER"))
						.jwt(builder -> builder.subject(intruder.getEmail())
								.claim("userId", intruder.getId().toString()))))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/v1/payments/" + paymentId)
				.with(jwt()
						.authorities(new SimpleGrantedAuthority("ROLE_USER"))
						.jwt(builder -> builder.subject(testUser.getEmail())
								.claim("userId", testUser.getId().toString()))))
				.andExpect(status().isOk());
	}
}
