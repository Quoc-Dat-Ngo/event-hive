package com.eventhive.integration;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.eventhive.AbstractWebIntegrationTest;
import com.eventhive.users.AuthProvider;
import com.eventhive.users.User;
import com.eventhive.users.UserRepository;
import com.eventhive.users.UserRole;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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

	private User testUser;

	@BeforeEach
	void setUpUser() {
		testUser = new User("Kevin", "Ngo", "kevin@example.com",
				passwordEncoder.encode("pass123"), AuthProvider.LOCAL, UserRole.USER);
		userRepository.saveAndFlush(testUser);
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
				.andExpect(jsonPath("$.id").exists())
				.andReturn();

		JsonNode rootNode = objectMapper.readTree(result.getResponse().getContentAsString());
		return rootNode.get("id").asString();
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
				    "status": "PENDING",
				    "eventId": "%s",
				    "seatId": "%s"
				}
				""", eventId, seatId));
	}

	@Test
	void shouldRequireAuthenticationWhenAccessingPaymentWithoutJwt() throws Exception {
		String venueId = createVenue();
		String seatId = createSeat(venueId);
		String eventId = createEvent(venueId);
		String bookingId = createBooking(eventId, seatId);

		String paymentJson = String.format("""
				{
				    "stripePaymentIntentId": "pi_123456789",
				    "amountCents": 20000,
				    "currency": "AUD",
				    "status": "SUCCEEDED",
				    "purchasedAt": "%s",
				    "refundedAt": null,
				    "bookingId": "%s"
				}
				""", Instant.now().toString(), bookingId);

		MvcResult result = mockMvc.perform(post("/api/v1/payments")
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
						.jwt(builder -> builder.subject(testUser.getEmail())
								.claim("userId", testUser.getId().toString())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(paymentJson))
				.andExpect(status().isOk())
				.andReturn();

		String paymentId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asString();

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

		String paymentJson = String.format("""
				{
				    "stripePaymentIntentId": "pi_123456789",
				    "amountCents": 20000,
				    "currency": "AUD",
				    "status": "SUCCEEDED",
				    "purchasedAt": "%s",
				    "refundedAt": null,
				    "bookingId": "%s"
				}
				""", Instant.now().toString(), bookingId);

		MvcResult result = mockMvc.perform(post("/api/v1/payments")
				.contentType(MediaType.APPLICATION_JSON)
				.content(paymentJson))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.amountCents").value(20000))
				.andExpect(jsonPath("$.currency").value("AUD"))
				.andExpect(jsonPath("$.status").value("SUCCEEDED"))
				.andExpect(jsonPath("$.bookingId").value(bookingId))
				.andReturn();

		String paymentId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id")
				.asString();

		mockMvc.perform(get("/api/v1/payments/" + paymentId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(paymentId))
				.andExpect(jsonPath("$.status").value("SUCCEEDED"));

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

		String paymentJson = String.format("""
				{
				    "stripePaymentIntentId": "pi_123456789",
				    "amountCents": 20000,
				    "currency": "AUD",
				    "status": "SUCCEEDED",
				    "purchasedAt": "%s",
				    "refundedAt": null,
				    "bookingId": "%s"
				}
				""", Instant.now().toString(), bookingId);

		String paymentId = objectMapper.readTree(mockMvc.perform(post("/api/v1/payments")
				.contentType(MediaType.APPLICATION_JSON)
				.content(paymentJson))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString()).get("id").asString();

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
	@WithMockUser(roles = "ADMIN")
	void shouldReturnNotFoundWhenBookingDoesNotExistOnPaymentCreation() throws Exception {
		UUID bookingId = UUID.randomUUID();
		String paymentJson = String.format("""
				{
				    "stripePaymentIntentId": "pi_123456789",
				    "amountCents": 20000,
				    "currency": "AUD",
				    "status": "SUCCEEDED",
				    "purchasedAt": "%s",
				    "refundedAt": null,
				    "bookingId": "%s"
				}
				""", Instant.now().toString(), bookingId);

		mockMvc.perform(post("/api/v1/payments")
				.contentType(MediaType.APPLICATION_JSON)
				.content(paymentJson))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.path").value("/api/v1/payments"))
				.andExpect(jsonPath("$.message")
						.value("Booking associated with this payment not found " + bookingId));
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void shouldReturnBadRequestWhenPaymentCurrencyIsInvalid() throws Exception {
		String venueId = createVenue();
		String seatId = createSeat(venueId);
		String eventId = createEvent(venueId);
		String bookingId = createBooking(eventId, seatId);

		String paymentJson = String.format("""
				{
				    "stripePaymentIntentId": "pi_123456789",
				    "amountCents": 20000,
				    "currency": "US",
				    "status": "SUCCEEDED",
				    "purchasedAt": "%s",
				    "refundedAt": null,
				    "bookingId": "%s"
				}
				""", Instant.now().toString(), bookingId);

		mockMvc.perform(post("/api/v1/payments")
				.contentType(MediaType.APPLICATION_JSON)
				.content(paymentJson))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.path").value("/api/v1/payments"))
				.andExpect(jsonPath("$.message")
						.value(containsString("currency: size must be between 3 and 3")));
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

		String paymentJson = String.format("""
				{
				"stripePaymentIntentId": "pi_123456789",
				"amountCents": 20000,
				"currency": "AUD",
				"status": "SUCCEEDED",
				"purchasedAt": "%s",
				"refundedAt": null,
				"bookingId": "%s"
				}
				""", Instant.now().toString(), bookingId);

		MvcResult result = mockMvc.perform(post("/api/v1/payments")
				.contentType(MediaType.APPLICATION_JSON)
				.content(paymentJson))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.amountCents").value(20000))
				.andExpect(jsonPath("$.currency").value("AUD"))
				.andExpect(jsonPath("$.status").value("SUCCEEDED"))
				.andExpect(jsonPath("$.bookingId").value(bookingId))
				.andReturn();

		String paymentId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id")
				.asString();

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
