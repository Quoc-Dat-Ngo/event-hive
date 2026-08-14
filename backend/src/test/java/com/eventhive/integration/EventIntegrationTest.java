package com.eventhive.integration;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.eventhive.AbstractWebIntegrationTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
@ActiveProfiles("test")
public class EventIntegrationTest extends AbstractWebIntegrationTest {
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

        @Test
        void shouldRequireAuthenticationWhenCreatingEvent() throws Exception {
                String venueId = extractIdFromMockMvc("/api/v1/venues", """
                                {
                                    "name": "CBD",
                                    "capacity": 10000,
                                    "location": "Parramata, Sydney"
                                }
                                """);

                Instant startsAt = Instant.now().plusSeconds(3600);
                Instant endsAt = Instant.now().plusSeconds(7200);
                String eventJson = String.format("""
                                {
                                    "title": "Euniverse",
                                    "purpose": "Park Eun Bin fan meeting",
                                    "startsAt": "%s",
                                    "endsAt": "%s",
                                    "status": "PUBLISHED",
                                    "venueId": "%s"
                                }
                                """, startsAt, endsAt, venueId);

                mockMvc.perform(post("/api/v1/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(eventJson))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void shouldReturnForbiddenWhenUserWithoutOrganizerRoleCreatesEvent() throws Exception {
                String venueId = extractIdFromMockMvc("/api/v1/venues", """
                                {
                                    "name": "CBD",
                                    "capacity": 10000,
                                    "location": "Parramata, Sydney"
                                }
                                """);

                Instant startsAt = Instant.now().plusSeconds(3600);
                Instant endsAt = Instant.now().plusSeconds(7200);
                String eventJson = String.format("""
                                {
                                    "title": "Euniverse",
                                    "purpose": "Park Eun Bin fan meeting",
                                    "startsAt": "%s",
                                    "endsAt": "%s",
                                    "status": "PUBLISHED",
                                    "venueId": "%s"
                                }
                                """, startsAt, endsAt, venueId);

                mockMvc.perform(post("/api/v1/events")
                                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(eventJson))
                                .andExpect(status().isForbidden());
        }

        @Test
        void shouldAllowOrganizerToReadEventBookings() throws Exception {
                String venueId = extractIdFromMockMvc("/api/v1/venues", """
                                {
                                    "name": "CBD",
                                    "capacity": 10000,
                                    "location": "Parramata, Sydney"
                                }
                                """);

                String eventId = extractIdFromMockMvc("/api/v1/events", String.format("""
                                {
                                    "title": "Euniverse",
                                    "purpose": "Park Eun Bin fan meeting",
                                    "startsAt": "%s",
                                    "endsAt": "%s",
                                    "status": "PUBLISHED",
                                    "venueId": "%s"
                                }
                                """, Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200), venueId));

                mockMvc.perform(get("/api/v1/events/" + eventId + "/bookings")
                                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_EVENT_ORGANISER"))))
                                .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        void shouldCreateReadUpdateAndDeleteEvent() throws Exception {
                String venueId = extractIdFromMockMvc("/api/v1/venues", """
                                {
                                    "name": "CBD",
                                    "capacity": 10000,
                                    "location": "Parramata, Sydney"
                                }
                                """);

                Instant startsAt = Instant.now().plusSeconds(3600);
                Instant endsAt = Instant.now().plusSeconds(7200);
                String eventJson = String.format("""
                                {
                                    "title": "Euniverse",
                                    "purpose": "Park Eun Bin fan meeting",
                                    "startsAt": "%s",
                                    "endsAt": "%s",
                                    "status": "PUBLISHED",
                                    "venueId": "%s"
                                }
                                """, startsAt, endsAt, venueId);

                MvcResult result = mockMvc.perform(post("/api/v1/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(eventJson))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.id").exists())
                                .andExpect(jsonPath("$.title").value("Euniverse"))
                                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                                .andExpect(jsonPath("$.venueId").value(venueId))
                                .andReturn();

                String eventId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asString();

                mockMvc.perform(get("/api/v1/events/" + eventId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.title").value("Euniverse"))
                                .andExpect(jsonPath("$.venueId").value(venueId));

                String updatedEventJson = """
                                {
                                    "title": "Euniverse Live",
                                    "purpose": "Park Eun Bin international fan meet"
                                }
                                """;

                mockMvc.perform(put("/api/v1/events/" + eventId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updatedEventJson))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.title").value("Euniverse Live"))
                                .andExpect(jsonPath("$.purpose").value("Park Eun Bin international fan meet"));

                mockMvc.perform(delete("/api/v1/events/" + eventId))
                                .andExpect(status().isNoContent());

                mockMvc.perform(get("/api/v1/events/" + eventId))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.path").value("/api/v1/events/" + eventId));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        void shouldReturnBadRequestWhenEventTimesAreInvalidOnCreate() throws Exception {
                String venueId = extractIdFromMockMvc("/api/v1/venues", """
                                {
                                    "name": "CBD",
                                    "capacity": 10000,
                                    "location": "Parramata, Sydney"
                                }
                                """);

                Instant startsAt = Instant.now().plusSeconds(7200);
                Instant endsAt = Instant.now().plusSeconds(3600);
                String invalidEventJson = String.format("""
                                {
                                    "title": "Euniverse",
                                    "purpose": "Park Eun Bin fan meeting",
                                    "startsAt": "%s",
                                    "endsAt": "%s",
                                    "status": "PUBLISHED",
                                    "venueId": "%s"
                                }
                                """, startsAt, endsAt, venueId);

                mockMvc.perform(post("/api/v1/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidEventJson))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.path").value("/api/v1/events"))
                                .andExpect(jsonPath("$.message")
                                                .value("Event ending time must strictly occur after starting time"))
                                .andExpect(jsonPath("$.statusCode").value(400));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        void shouldReturnBadRequestWhenUpdatingTimeForCompletedEvent() throws Exception {
                String venueId = extractIdFromMockMvc("/api/v1/venues", """
                                {
                                    "name": "CBD",
                                    "capacity": 10000,
                                    "location": "Parramata, Sydney"
                                }
                                """);

                Instant startsAt = Instant.now().plusSeconds(3600);
                Instant endsAt = Instant.now().plusSeconds(7200);
                String eventJson = String.format("""
                                {
                                    "title": "Euniverse",
                                    "purpose": "Park Eun Bin fan meeting",
                                    "startsAt": "%s",
                                    "endsAt": "%s",
                                    "status": "COMPLETED",
                                    "venueId": "%s"
                                }
                                """, startsAt, endsAt, venueId);

                String eventId = extractIdFromMockMvc("/api/v1/events", eventJson);

                Instant updatedStartsAt = Instant.now().plusSeconds(10800);
                Instant updatedEndsAt = Instant.now().plusSeconds(14400);
                String invalidUpdateJson = String.format("""
                                {
                                    "startsAt": "%s",
                                    "endsAt": "%s"
                                }
                                """, updatedStartsAt, updatedEndsAt);

                mockMvc.perform(put("/api/v1/events/" + eventId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidUpdateJson))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.path").value("/api/v1/events/" + eventId))
                                .andExpect(jsonPath("$.message")
                                                .value("Cannot modify ending time of a completed event"))
                                .andExpect(jsonPath("$.statusCode").value(400));
        }
}
