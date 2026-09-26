package com.eventhive.helpers;

import org.springframework.jdbc.core.JdbcTemplate;

public class TestUtility {
    public static final void clearDatabase(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                    TRUNCATE TABLE
                    payments,
                    price_tier_seats,
                    price_tiers,
                    bookings,
                    seats,
                    events,
                    users,
                    venues
                    RESTART IDENTITY CASCADE;
                """);
    }
}
