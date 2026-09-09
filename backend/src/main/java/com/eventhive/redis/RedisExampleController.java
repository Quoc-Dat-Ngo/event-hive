package com.eventhive.redis;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/redis-example")
@RequiredArgsConstructor
public class RedisExampleController {
    private final RedisExampleService redisExampleService;

    @PostMapping
    @ResponseStatus(code = HttpStatus.CREATED)
    public void setNewValue() {
        redisExampleService.saveValue();
    }

    @GetMapping
    public String getExistingValue() {
        return redisExampleService.getValue();
    }

    @DeleteMapping
    public boolean deleteKeyByScript() {
        return redisExampleService.deleteKeyByScript();
    }
}
