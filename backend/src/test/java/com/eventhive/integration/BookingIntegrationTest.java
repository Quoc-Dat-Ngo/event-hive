package com.eventhive.integration;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.eventhive.AbstractWebIntegrationTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
public class BookingIntegrationTest extends AbstractWebIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String userId;
    private String seatId;
    private String eventId;
    private String venueId;

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

    @BeforeEach
    void setUpData() throws Exception {
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
        this.userId = extractIdFromMockMvc("/api/v1/users", """
                {
                    "firstName": "Kevin",
                    "lastName": "Ngo",
                    "email": "quocngo@gmail.com",
                    "password": "dat123",
                    "authProvider": "LOCAL",
                    "role": "USER"
                }
                """);
        this.eventId = extractIdFromMockMvc("/api/v1/events", String.format("""
                        {
                            "title": "Euniverse",
                            "purpose": "Park Eun Bin fan meeting",
                            "startsAt": "2026-08-10T14:30:00+10:00",
                            "endsAt": "2026-09-10T14:30:00+10:00",
                            "status": "PUBLISHED",
                            "venueId": "%s"
                        }
                """, venueId));
    }

    @Test
    void shouldPreventBookingWithSameSeatAndEventPairUnderConcurrentCondition() throws Exception {
        String bookingAJson = String.format("""
                        {
                            "priceCents": 20000,
                            "status": "PENDING",
                            "userId": "%s",
                            "eventId": "%s",
                            "seatId": "%s"
                        }
                """, userId, eventId, seatId);

        String bookingBJson = String.format("""
                        {
                            "priceCents": 20000,
                            "status": "PENDING",
                            "userId": "%s",
                            "eventId": "%s",
                            "seatId": "%s"
                        }
                """, userId, eventId, seatId);

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Callable<Integer> requestA = () -> {
            readyLatch.countDown();
            startLatch.await();
            return mockMvc.perform(post("/api/v1/bookings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(bookingAJson)).andReturn().getResponse().getStatus();

        };
        Callable<Integer> requestB = () -> {
            readyLatch.countDown();
            startLatch.await();
            return mockMvc.perform(post("/api/v1/bookings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(bookingBJson)).andReturn().getResponse().getStatus();
        };

        Future<Integer> resultA = executorService.submit(requestA);
        Future<Integer> resultB = executorService.submit(requestB);

        readyLatch.await();
        startLatch.countDown();

        List<Integer> statuses = List.of(resultA.get(), resultB.get());
        executorService.shutdown();

        assertThat(statuses).contains(201, 409);
    }
}
