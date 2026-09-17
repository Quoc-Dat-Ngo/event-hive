package com.eventhive.integration;

import com.eventhive.AbstractWebIntegrationTest;
import com.eventhive.redis.SeatLockService;
import com.eventhive.users.AuthProvider;
import com.eventhive.users.User;
import com.eventhive.users.UserRepository;
import com.eventhive.users.UserRole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.eventhive.stripe.StripeHostedCheckoutService;
import com.stripe.model.checkout.Session;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
public class RedisLockTest extends AbstractWebIntegrationTest {
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SeatLockService seatLockService;

    @MockitoBean
    private StripeHostedCheckoutService checkoutService;

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
    void setupData() throws Exception {
        Session fakeSession = new Session();
        fakeSession.setId("cs_test_" + UUID.randomUUID());
        fakeSession.setUrl("https://checkout.stripe.com/c/pay/cs_test_mock");
        fakeSession.setPaymentIntent("pi_test_" + UUID.randomUUID());
        when(checkoutService.checkout(any())).thenReturn(fakeSession);

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
                """, Instant.now().plus(1, ChronoUnit.DAYS), Instant.now().plus(2, ChronoUnit.DAYS),
                venueId));
    }

    @Test
    void shouldLockKeyWithFiveMinuteTimeout() throws Exception {
        String bookingJson = String.format("""
                {
                    "priceCents": 20000,
                    "eventId": "%s",
                    "seatId": "%s"
                }
                """, eventId, seatId);
        mockMvc.perform(post("/api/v1/bookings")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
                        .jwt(builder -> builder.subject(user.getEmail())
                                .claim("userId", user.getId().toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookingJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.booking.id").exists())
                .andExpect(jsonPath("$.booking.priceCents").exists())
                .andExpect(jsonPath("$.booking.status").exists())
                .andExpect(jsonPath("$.booking.userId").exists())
                .andExpect(jsonPath("$.booking.eventId").exists())
                .andExpect(jsonPath("$.booking.seatId").exists());

        Long expireTimeSeconds = redisTemplate.getExpire("seat-lock:" + eventId + ":" + seatId, TimeUnit.SECONDS);

        assertThat(expireTimeSeconds).isNotNull();
        assertThat(expireTimeSeconds).isGreaterThan(290);
        assertThat(expireTimeSeconds).isLessThan(300);
    }

    @Test
    void shouldOnlyAllowOneThreadToAcquireLockConcurrently() throws Exception {
        UUID seatUuid = UUID.fromString(seatId);
        UUID eventUuid = UUID.fromString(eventId);
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicBoolean firstResult = new AtomicBoolean();
        AtomicBoolean secondResult = new AtomicBoolean();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            executor.submit(() -> {
                readyLatch.countDown();
                awaitUninterruptibly(startLatch);
                firstResult.set(seatLockService.tryLock(seatUuid, eventUuid, firstUserId));
            });
            executor.submit(() -> {
                readyLatch.countDown();
                awaitUninterruptibly(startLatch);
                secondResult.set(seatLockService.tryLock(seatUuid, eventUuid, secondUserId));
            });

            readyLatch.await();
            startLatch.countDown();
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(firstResult.get() ^ secondResult.get()).isTrue();
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
