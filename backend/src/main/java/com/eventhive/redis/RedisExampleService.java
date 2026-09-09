package com.eventhive.redis;

import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RedisExampleService {
    private final RedisTemplate<String, String> redisTemplate;

    public void saveValue() {
        redisTemplate.opsForValue().set("hello", "world");
    }

    public String getValue() {
        return redisTemplate.opsForValue().get("hello");
    }

    public boolean deleteKeyByScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/releaseLock.lua"));
        script.setResultType(Long.class);

        Long result = redisTemplate.execute(script, List.of("hello"), "world");
        return Long.valueOf(1L).equals(result);
    }
}
