package com.eventhive.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.eventhive.AbstractWebIntegrationTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
public class SeatIntegrationTest extends AbstractWebIntegrationTest {
	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

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

	@Test
	void shouldRequireAuthenticationWhenCreatingSeat() throws Exception {
		String venueId = createVenue();
		String newSeatJson = String.format("""
				{
				    "seatRow": "AB",
				    "number": 2,
				    "venueId": "%s"
				}
				""", venueId);

		mockMvc.perform(post("/api/v1/seats")
				.contentType(MediaType.APPLICATION_JSON)
				.content(newSeatJson))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void shouldReturnForbiddenWhenRegularUserCreatesSeat() throws Exception {
		String venueId = createVenue();
		String newSeatJson = String.format("""
				{
				    "seatRow": "AB",
				    "number": 2,
				    "venueId": "%s"
				}
				""", venueId);

		mockMvc.perform(post("/api/v1/seats")
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
				.contentType(MediaType.APPLICATION_JSON)
				.content(newSeatJson))
				.andExpect(status().isForbidden());
	}

	@Test
	void shouldAllowAdminToCreateSeat() throws Exception {
		String venueId = createVenue();
		String newSeatJson = String.format("""
				{
				    "seatRow": "AB",
				    "number": 2,
				    "venueId": "%s"
				}
				""", venueId);

		mockMvc.perform(post("/api/v1/seats")
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
				.contentType(MediaType.APPLICATION_JSON)
				.content(newSeatJson))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists());
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void shouldReturnConflictStatusUponViolatingCompositeUniqueConstraint() throws Exception {
		String venueId = createVenue();
		String newSeatJson = String.format("""
				{
				    "seatRow": "AB",
				    "number": 2,
				    "venueId": "%s"
				}
				""", venueId);

		mockMvc.perform(post("/api/v1/seats")
				.contentType(MediaType.APPLICATION_JSON)
				.content(newSeatJson))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists());

		// Database-level composite unqique constraint (seat_row, number, venue_id)
		mockMvc.perform(post("/api/v1/seats")
				.contentType(MediaType.APPLICATION_JSON)
				.content(newSeatJson))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.path").value("/api/v1/seats"))
				.andExpect(jsonPath("$.statusCode").value(409));
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void shouldStoreSeatRowAsUpperCaseInDb() throws Exception {
		String venueId = createVenue();
		String seatId = createSeat(venueId);

		String updateSeatJson = """
				{
					"seatRow": "a"
				}
				""";

		mockMvc.perform(put("/api/v1/seats/" + seatId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(updateSeatJson))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.seatRow").value("A"));

	}
}
