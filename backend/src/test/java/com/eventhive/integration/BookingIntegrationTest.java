package com.eventhive.integration;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.eventhive.AbstractWebIntegrationTest;
import com.eventhive.bookings.BookingRepository;
import com.eventhive.users.AuthProvider;
import com.eventhive.users.User;
import com.eventhive.users.UserRepository;
import com.eventhive.users.UserRole;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
public class BookingIntegrationTest extends AbstractWebIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    private User user;
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
        user = new User("Kevin", "Ngo", "kevin@example.com",
                passwordEncoder.encode("pass123"), AuthProvider.LOCAL, UserRole.USER);
        userRepository.saveAndFlush(user);

        this.eventId = extractIdFromMockMvc("/api/v1/events", String.format("""
                        {
                            "title": "Euniverse",
                            "purpose": "Park Eun Bin fan meeting",
                            "startsAt": "%s",
                            "endsAt": "%s",
                            "status": "PUBLISHED",
                            "venueId": "%s"
                        }
                """, Instant.now().plus(1, ChronoUnit.DAYS), Instant.now().plus(2, ChronoUnit.DAYS), venueId));
    }

    @Test
    void shouldRequireAuthenticationWhenAccessingBookingWithoutJwt() throws Exception {
        String bookingJson = String.format("""
                        {
                            "priceCents": 20000,
                            "status": "PENDING",
                            "eventId": "%s",
                            "seatId": "%s"
                        }
                """, eventId, seatId);

        MvcResult result = mockMvc.perform(post("/api/v1/bookings")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
                        .jwt(builder -> builder.subject(user.getEmail())
                                .claim("userId", user.getId().toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookingJson))
                .andExpect(status().isCreated())
                .andReturn();

        String bookingId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asString();

        mockMvc.perform(get("/api/v1/bookings/" + bookingId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnForbiddenWhenNonOwnerViewsBooking() throws Exception {
        User intruder = new User("Intruder", "User", "intruder@example.com",
                passwordEncoder.encode("pass123"), AuthProvider.LOCAL, UserRole.USER);
        userRepository.saveAndFlush(intruder);

        String bookingJson = String.format("""
                        {
                            "priceCents": 20000,
                            "status": "PENDING",
                            "eventId": "%s",
                            "seatId": "%s"
                        }
                """, eventId, seatId);

        MvcResult result = mockMvc.perform(post("/api/v1/bookings")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
                        .jwt(builder -> builder.subject(user.getEmail())
                                .claim("userId", user.getId().toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookingJson))
                .andExpect(status().isCreated())
                .andReturn();

        String bookingId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asString();

        mockMvc.perform(get("/api/v1/bookings/" + bookingId)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
                        .jwt(builder -> builder.subject(intruder.getEmail())
                                .claim("userId", intruder.getId().toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowOwnerToViewBooking() throws Exception {
        String bookingJson = String.format("""
                        {
                            "priceCents": 20000,
                            "status": "PENDING",
                            "eventId": "%s",
                            "seatId": "%s"
                        }
                """, eventId, seatId);

        MvcResult result = mockMvc.perform(post("/api/v1/bookings")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
                        .jwt(builder -> builder.subject(user.getEmail())
                                .claim("userId", user.getId().toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookingJson))
                .andExpect(status().isCreated())
                .andReturn();

        String bookingId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asString();

        mockMvc.perform(get("/api/v1/bookings/" + bookingId)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
                        .jwt(builder -> builder.subject(user.getEmail())
                                .claim("userId", user.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId));
    }

    @Test
    void shouldPreventBookingWithSameSeatAndEventPairUnderConcurrentCondition() throws Exception {
        String bookingAJson = String.format("""
                        {
                            "priceCents": 20000,
                            "status": "PENDING",
                            "eventId": "%s",
                            "seatId": "%s"
                        }
                """, eventId, seatId);

        String bookingBJson = String.format("""
                        {
                            "priceCents": 20000,
                            "status": "PENDING",
                            "eventId": "%s",
                            "seatId": "%s"
                        }
                """, eventId, seatId);

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Callable<Integer> requestA = () -> {
            readyLatch.countDown();
            startLatch.await();
            return mockMvc.perform(post("/api/v1/bookings")
                    .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
                            .jwt(builder -> builder.subject(user.getEmail())
                                    .claim("userId", user.getId().toString())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(bookingAJson)).andReturn().getResponse().getStatus();

        };
        Callable<Integer> requestB = () -> {
            readyLatch.countDown();
            startLatch.await();
            return mockMvc.perform(post("/api/v1/bookings")
                    .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
                            .jwt(builder -> builder.subject(user.getEmail())
                                    .claim("userId", user.getId().toString())))
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
        assertThat(bookingRepository.findAllBookingsByEventId(UUID.fromString(eventId))).hasSize(1);
    }
}
