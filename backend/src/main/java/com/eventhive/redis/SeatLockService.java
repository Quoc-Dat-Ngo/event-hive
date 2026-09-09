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

    public boolean tryLock(UUID seatId, UUID userId) {
        String key = "seat-lock:" + seatId.toString();
        String value = userId.toString();

        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, value, Duration.ofMinutes(5));

        return Boolean.TRUE.equals(acquired);
    }

    public boolean realeaseLock(UUID seatId, UUID userId) {
        String key = "seat-lock:" + seatId.toString();
        String expectedValue = userId.toString();

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/releaseLock.lua"));
        script.setResultType(Long.class);

        Long released = redisTemplate.execute(script, List.of(key), expectedValue);
        return Long.valueOf(1L).equals(released);
    }
}
