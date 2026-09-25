package com.eventhive.redis;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SeatLockService {
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * How long a seat is held for one checkout attempt. Matches the Stripe Checkout
     * Session lifetime (Stripe's minimum is 30 min), because the PENDING booking
     * blocks the seat in the DB for that long anyway - a shorter lock would only
     * turn a clear "seat reserved" error into a DB constraint violation.
     */
    public static final Duration SEAT_HOLD = Duration.ofMinutes(31);

    public boolean tryLock(UUID seatId, UUID eventId, UUID userId) {
        String key = "seat-lock:" + eventId.toString() + ":" + seatId.toString();
        String value = userId.toString();

        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, value, SEAT_HOLD);

        return Boolean.TRUE.equals(acquired);
    }

    public boolean releaseLock(UUID seatId, UUID eventId, UUID userId) {
        String key = "seat-lock:" + eventId.toString() + ":" + seatId.toString();
        String expectedValue = userId.toString();

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/releaseLock.lua"));
        script.setResultType(Long.class);

        Long released = redisTemplate.execute(script, List.of(key), expectedValue);
        return Long.valueOf(1L).equals(released);
    }
}
