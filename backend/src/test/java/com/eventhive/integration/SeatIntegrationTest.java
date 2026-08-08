package com.eventhive.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.eventhive.AbstractWebIntegrationTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
public class SeatIntegrationTest extends AbstractWebIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldReturnConflictStatusUponViolatingCompositeUniqueConstraint() throws Exception {
        String newVenueJson = """
                {
                    "name": "CBD",
                    "capacity": 10000,
                    "location": "Parramata, Sydney"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/v1/venues")
                .contentType(MediaType.APPLICATION_JSON)
                .content(newVenueJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("CBD"))
                .andExpect(jsonPath("$.capacity").value(10000))
                .andExpect(jsonPath("$.location").value("Parramata, Sydney"))
                .andReturn();

        String jsonResponse = result.getResponse().getContentAsString();
        JsonNode rootNode = objectMapper.readTree(jsonResponse);
        String venueId = rootNode.get("id").asString();

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
}
