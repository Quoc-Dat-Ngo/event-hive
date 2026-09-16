package com.eventhive.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.eventhive.venues.Venue;
import com.eventhive.venues.VenueRepository;

import lombok.RequiredArgsConstructor;

@Component
@Order(1)
@ConditionalOnProperty(name = "eventhive.seed.enabled", havingValue = "true")
@RequiredArgsConstructor
public class VenueSeeder implements ApplicationRunner {
    public static final String SEED_VENUE_NAME = "Hive Arena";
    public static final String SEED_VENUE_LOCATION = "123 Collins Street, Melbourne VIC 3000";
    public static final int SEED_VENUE_CAPACITY = SeatSeeder.SEAT_ROWS.length * SeatSeeder.SEATS_PER_ROW;

    private final VenueRepository venueRepository;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!venueRepository.existsByName(SEED_VENUE_NAME)) {
            venueRepository.save(new Venue(SEED_VENUE_NAME, SEED_VENUE_CAPACITY, SEED_VENUE_LOCATION));
            System.out.println("Seed venue '" + SEED_VENUE_NAME + "' generated successfully.");
        }
    }
}
