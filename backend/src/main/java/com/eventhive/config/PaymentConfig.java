package com.eventhive.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import com.stripe.Stripe;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class PaymentConfig {

    @Value("${stripe.secret}")
    private String secretKey;

    @PostConstruct
    public void setSecretKey() {
        Stripe.apiKey = secretKey;
        log.info("Stripe API initialized (key: {}...)", secretKey.substring(0, 7));
    }
}
