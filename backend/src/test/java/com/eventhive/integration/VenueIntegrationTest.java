package com.eventhive.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.eventhive.AbstractWebIntegrationTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
public class VenueIntegrationTest extends AbstractWebIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String extractIdFromMockMvc(String uri, String json) throws Exception {
        MvcResult result = mockMvc.perform(post(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn();

        JsonNode rootNode = objectMapper.readTree(result.getResponse().getContentAsString());
        return rootNode.get("id").asString();
    }

    @Test
    void shouldCreateReadUpdateAndDeleteVenue() throws Exception {
        String venueJson = """
                {
                    "name": "CBD",
                    "capacity": 10000,
                    "location": "Parramata, Sydney"
                }
                """;

        String venueId = extractIdFromMockMvc("/api/v1/venues", venueJson);

        mockMvc.perform(get("/api/v1/venues/" + venueId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("CBD"))
                .andExpect(jsonPath("$.capacity").value(10000))
                .andExpect(jsonPath("$.location").value("Parramata, Sydney"));

        String updatedVenueJson = """
                {
                    "name": "CBD Arena",
                    "capacity": 12000,
                    "location": "Parramata, NSW"
                }
                """;

        mockMvc.perform(put("/api/v1/venues/" + venueId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatedVenueJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("CBD Arena"))
                .andExpect(jsonPath("$.capacity").value(12000))
                .andExpect(jsonPath("$.location").value("Parramata, NSW"));

        mockMvc.perform(delete("/api/v1/venues/" + venueId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/venues/" + venueId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.path").value("/api/v1/venues/" + venueId));
    }

    @Test
    void shouldReturnBadRequestWhenVenueValidationFails() throws Exception {
        String invalidVenueJson = """
                {
                    "name": "",
                    "capacity": -5,
                    "location": ""
                }
                """;

        mockMvc.perform(post("/api/v1/venues")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidVenueJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.path").value("/api/v1/venues"))
                .andExpect(jsonPath("$.message").value(containsString("Venue name cannot be blank")))
                .andExpect(jsonPath("$.message")
                        .value(containsString("Venue capacity must be explicitly greater than or equal to zero")))
                .andExpect(jsonPath("$.message").value(containsString("Venue location cannot be blank")));
    }
}
