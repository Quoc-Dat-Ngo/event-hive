package com.eventhive.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.eventhive.seats.Seat;
import com.eventhive.seats.SeatRepository;
import com.eventhive.venues.Venue;
import com.eventhive.venues.VenueRepository;

import lombok.RequiredArgsConstructor;

@Component
@Order(2)
@ConditionalOnProperty(name = "eventhive.seed.enabled", havingValue = "true")
@RequiredArgsConstructor
public class SeatSeeder implements ApplicationRunner {
    public static final String[] SEAT_ROWS = { "A", "B" };
    public static final int SEATS_PER_ROW = 5;

    private final VenueRepository venueRepository;
    private final SeatRepository seatRepository;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Venue venue = venueRepository.findByName(VenueSeeder.SEED_VENUE_NAME)
                .orElseThrow(() -> new IllegalStateException(
                        "Seed venue '" + VenueSeeder.SEED_VENUE_NAME + "' not found; VenueSeeder must run first."));

        if (seatRepository.existsByVenue_Id(venue.getId())) {
            return;
        }

        List<Seat> seats = new ArrayList<>();
        for (String row : SEAT_ROWS) {
            for (int number = 1; number <= SEATS_PER_ROW; number++) {
                seats.add(new Seat(row, number, venue));
            }
        }
        seatRepository.saveAll(seats);
        System.out.println(seats.size() + " seed seats generated successfully for '" + venue.getName() + "'.");
    }
}
