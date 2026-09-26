package com.eventhive.integration;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.eventhive.AbstractWebIntegrationTest;
import com.eventhive.bookings.Booking;
import com.eventhive.stripe.StripeHostedCheckoutService;
import com.eventhive.users.AuthProvider;
import com.eventhive.users.User;
import com.eventhive.users.UserRepository;
import com.eventhive.users.UserRole;
import com.stripe.model.checkout.Session;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
public class PriceTierIntegrationTest extends AbstractWebIntegrationTest {
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

	private String eventId;
	private User customer;
	// "A1" -> seat id
	private final Map<String, String> seats = new HashMap<>();

	private String send(String uri, String role, String json, int expectedStatus) throws Exception {
		return mockMvc.perform(post(uri)
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role)))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json))
				.andExpect(status().is(expectedStatus))
				.andReturn().getResponse().getContentAsString();
	}

	private String idOf(String json) {
		return objectMapper.readTree(json).get("id").asString();
	}

	@BeforeEach
	void setUpData() throws Exception {
		when(checkoutService.checkout(any())).thenAnswer(invocation -> {
			Booking booking = invocation.getArgument(0);
			Session session = new Session();
			session.setId("cs_test_" + booking.getId());
			session.setUrl("https://checkout.stripe.com/c/pay/cs_test_" + booking.getId());
			return session;
		});

		String venueId = idOf(send("/api/v1/venues", "ADMIN", """
				{ "name": "CBD", "capacity": 100, "location": "Parramatta, Sydney" }
				""", 201));
		for (String row : new String[] { "A", "B", "Z", "AA" }) {
			for (int number = 1; number <= 3; number++) {
				String id = idOf(send("/api/v1/seats", "ADMIN", String.format("""
						{ "seatRow": "%s", "number": %d, "venueId": "%s" }
						""", row, number, venueId), 201));
				seats.put(row + number, id);
			}
		}
		eventId = idOf(send("/api/v1/events", "ADMIN", String.format("""
				{
				    "title": "Euniverse",
				    "startsAt": "%s",
				    "endsAt": "%s",
				    "status": "PUBLISHED",
				    "venueId": "%s"
				}
				""", Instant.now().plus(7, ChronoUnit.DAYS), Instant.now().plus(8, ChronoUnit.DAYS), venueId), 201));

		customer = new User("Kevin", "Ngo", "kevin@example.com", passwordEncoder.encode("pass123"),
				AuthProvider.LOCAL, UserRole.USER);
		userRepository.saveAndFlush(customer);
	}

	private String tiersUri() {
		return "/api/v1/events/" + eventId + "/tiers";
	}

	private String createTier(String name, int priceCents, String rangesJson) throws Exception {
		return idOf(send(tiersUri(), "EVENT_ORGANISER", String.format("""
				{ "name": "%s", "priceCents": %d, "seatRanges": %s }
				""", name, priceCents, rangesJson), 201));
	}

	private ResultActions book(String seatKey, String extraJson) throws Exception {
		return mockMvc.perform(post("/api/v1/bookings")
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
						.jwt(builder -> builder.subject(customer.getEmail())
								.claim("userId", customer.getId().toString())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(String.format("""
						{ "eventId": "%s", "seatId": "%s" %s }
						""", eventId, seats.get(seatKey), extraJson)));
	}

	private JsonNode seatMap() throws Exception {
		String json = mockMvc.perform(get("/api/v1/events/" + eventId + "/seats")
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json);
	}

	private JsonNode seatEntry(JsonNode map, String seatKey) {
		for (JsonNode seat : map) {
			if (seat.get("seatId").asString().equals(seats.get(seatKey))) {
				return seat;
			}
		}
		throw new AssertionError("Seat not in map " + seatKey);
	}

	@Test
	void shouldCreateTiersFromSeatRangesAndExposeThemOnSeatMap() throws Exception {
		// Rows A..B, seats 1-2 only; row Z..AA whole rows (Z sorts before AA)
		String front = createTier("Front", 30000, """
				[{ "rowFrom": "a", "rowTo": "B", "numberFrom": 1, "numberTo": 2 }]
				""");
		createTier("Back", 8000, """
				[{ "rowFrom": "Z", "rowTo": "AA" }]
				""");

		mockMvc.perform(get(tiersUri()).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				// Most expensive first
				.andExpect(jsonPath("$[0].name").value("Front"))
				.andExpect(jsonPath("$[0].seatCount").value(4))
				.andExpect(jsonPath("$[1].name").value("Back"))
				.andExpect(jsonPath("$[1].seatCount").value(6));

		JsonNode map = seatMap();
		assertThat(map).hasSize(12);
		// Ordered A, B, Z, AA
		assertThat(map.get(0).get("seatRow").asString()).isEqualTo("A");
		assertThat(map.get(11).get("seatRow").asString()).isEqualTo("AA");

		assertThat(seatEntry(map, "A1").get("tierId").asString()).isEqualTo(front);
		assertThat(seatEntry(map, "A1").get("priceCents").asInt()).isEqualTo(30000);
		assertThat(seatEntry(map, "A1").get("available").asBoolean()).isTrue();
		// Seat 3 of rows A/B is in no tier, so it is not on sale
		assertThat(seatEntry(map, "B3").get("tierId").isNull()).isTrue();
		assertThat(seatEntry(map, "B3").get("available").asBoolean()).isFalse();
		assertThat(seatEntry(map, "AA3").get("tierName").asString()).isEqualTo("Back");
	}

	@Test
	void shouldForbidCustomersFromManagingTiers() throws Exception {
		send(tiersUri(), "USER", """
				{ "name": "Front", "priceCents": 30000, "seatRanges": [{ "rowFrom": "A" }] }
				""", 403);
	}

	@Test
	void shouldRejectOverlappingTiers() throws Exception {
		createTier("Front", 30000, """
				[{ "rowFrom": "A" }]
				""");

		String body = send(tiersUri(), "EVENT_ORGANISER", """
				{ "name": "Premium", "priceCents": 50000, "seatRanges": [{ "rowFrom": "A", "numberFrom": 3 }] }
				""", 409);
		assertThat(objectMapper.readTree(body).get("message").asString())
				.isEqualTo("Seats already belong to another tier: A3");
	}

	@Test
	void shouldRejectRangesMatchingNoSeatsOrReversed() throws Exception {
		send(tiersUri(), "EVENT_ORGANISER", """
				{ "name": "Ghost", "priceCents": 1000, "seatRanges": [{ "rowFrom": "Q" }] }
				""", 400);
		send(tiersUri(), "EVENT_ORGANISER", """
				{ "name": "Backwards", "priceCents": 1000, "seatRanges": [{ "rowFrom": "B", "rowTo": "A" }] }
				""", 400);
	}

	@Test
	void shouldChargeTierPriceRegardlessOfClientSuppliedPrice() throws Exception {
		createTier("Front", 30000, """
				[{ "rowFrom": "A" }]
				""");

		book("A2", ", \"priceCents\": 1")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.booking.priceCents").value(30000));

		assertThat(seatEntry(seatMap(), "A2").get("available").asBoolean()).isFalse();
	}

	@Test
	void shouldRejectBookingSeatWithoutTier() throws Exception {
		createTier("Front", 30000, """
				[{ "rowFrom": "A" }]
				""");

		book("B1", "")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Seat " + seats.get("B1") + " is not on sale for this event"));
	}

	@Test
	void shouldUpdateTierPriceWithoutChangingExistingBookings() throws Exception {
		String tierId = createTier("Front", 30000, """
				[{ "rowFrom": "A" }]
				""");
		String bookingJson = book("A1", "").andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String bookingId = objectMapper.readTree(bookingJson).get("booking").get("id").asString();

		mockMvc.perform(put(tiersUri() + "/" + tierId)
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_EVENT_ORGANISER")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "name": "Front row", "priceCents": 45000 }
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Front row"))
				.andExpect(jsonPath("$.priceCents").value(45000));

		mockMvc.perform(get("/api/v1/bookings/" + bookingId)
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
				.andExpect(jsonPath("$.priceCents").value(30000));
	}

	@Test
	void shouldNotDeleteTierWithActiveBookings() throws Exception {
		String tierId = createTier("Front", 30000, """
				[{ "rowFrom": "A" }]
				""");
		String emptyTierId = createTier("Back", 8000, """
				[{ "rowFrom": "B" }]
				""");
		book("A1", "").andExpect(status().isCreated());

		mockMvc.perform(delete(tiersUri() + "/" + tierId)
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
				.andExpect(status().isConflict());

		mockMvc.perform(delete(tiersUri() + "/" + emptyTierId)
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
				.andExpect(status().isNoContent());
		assertThat(seatEntry(seatMap(), "B1").get("tierId").isNull()).isTrue();
	}

	@Test
	void shouldReturnNotFoundForTiersOfUnknownEvent() throws Exception {
		mockMvc.perform(get("/api/v1/events/" + UUID.randomUUID() + "/tiers")
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
				.andExpect(status().isNotFound());
	}
}
