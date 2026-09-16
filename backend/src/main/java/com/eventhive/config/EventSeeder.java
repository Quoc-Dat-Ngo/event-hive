package com.eventhive.config;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.eventhive.events.Event;
import com.eventhive.events.EventRepository;
import com.eventhive.events.EventStatus;
import com.eventhive.venues.Venue;
import com.eventhive.venues.VenueRepository;

import lombok.RequiredArgsConstructor;

@Component
@Order(3)
@ConditionalOnProperty(name = "eventhive.seed.enabled", havingValue = "true")
@RequiredArgsConstructor
public class EventSeeder implements ApplicationRunner {
    private final VenueRepository venueRepository;
    private final EventRepository eventRepository;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Venue venue = venueRepository.findByName(VenueSeeder.SEED_VENUE_NAME)
                .orElseThrow(() -> new IllegalStateException(
                        "Seed venue '" + VenueSeeder.SEED_VENUE_NAME + "' not found; VenueSeeder must run first."));

        if (eventRepository.existsByVenue_Id(venue.getId())) {
            return;
        }

        // Dates are relative to startup so seeded events are always upcoming (19:00 UTC)
        Instant today = Instant.now().truncatedTo(ChronoUnit.DAYS).plus(Duration.ofHours(19));

        List<Event> events = List.of(
                new Event("Midnight Jazz Session", "An evening of live jazz standards and improvisation",
                        today.plus(Duration.ofDays(14)), today.plus(Duration.ofDays(14)).plus(Duration.ofHours(3)),
                        "The Blue Note Quartet", EventStatus.PUBLISHED, venue),
                new Event("Indie Rock Night", "Local indie bands showcase",
                        today.plus(Duration.ofDays(30)), today.plus(Duration.ofDays(30)).plus(Duration.ofHours(4)),
                        "Velvet Echoes", EventStatus.PUBLISHED, venue),
                new Event("Tech Talks: Scaling Systems", "Talks on distributed systems and system design",
                        today.plus(Duration.ofDays(45)), today.plus(Duration.ofDays(45)).plus(Duration.ofHours(2)),
                        "EventHive Engineering", EventStatus.DRAFT, venue));

        eventRepository.saveAll(events);
        System.out.println(events.size() + " seed events generated successfully for '" + venue.getName() + "'.");
    }
}
