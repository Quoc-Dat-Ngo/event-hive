package com.eventhive.config;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.eventhive.events.Event;
import com.eventhive.events.EventRepository;
import com.eventhive.seats.Seat;
import com.eventhive.seats.SeatRepository;
import com.eventhive.tiers.PriceTier;
import com.eventhive.tiers.PriceTierRepository;
import com.eventhive.tiers.PriceTierSeat;
import com.eventhive.tiers.PriceTierSeatRepository;
import com.eventhive.venues.Venue;
import com.eventhive.venues.VenueRepository;

import lombok.RequiredArgsConstructor;

@Component
@Order(4)
@ConditionalOnProperty(name = "eventhive.seed.enabled", havingValue = "true")
@RequiredArgsConstructor
public class PriceTierSeeder implements ApplicationRunner {
    private final VenueRepository venueRepository;
    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;
    private final PriceTierRepository tierRepository;
    private final PriceTierSeatRepository tierSeatRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        Venue venue = venueRepository.findByName(VenueSeeder.SEED_VENUE_NAME)
                .orElseThrow(() -> new IllegalStateException(
                        "Seed venue '" + VenueSeeder.SEED_VENUE_NAME + "' not found; VenueSeeder must run first."));
        List<Seat> seats = seatRepository.findByVenueId(venue.getId());

        int seeded = 0;
        for (Event event : eventRepository.findAll()) {
            if (!event.getVenue().getId().equals(venue.getId())
                    || !tierRepository.findByEventIdOrderByPriceCentsDesc(event.getId()).isEmpty()) {
                continue;
            }
            // Row A is the front row, every other seeded row is standard
            seedTier(event, "Front row", 12000, seats.stream().filter(s -> s.getSeatRow().equals("A")).toList());
            seedTier(event, "Standard", 6000, seats.stream().filter(s -> !s.getSeatRow().equals("A")).toList());
            seeded++;
        }

        if (seeded > 0) {
            System.out.println("Seed price tiers generated for " + seeded + " event(s).");
        }
    }

    private void seedTier(Event event, String name, int priceCents, List<Seat> seats) {
        PriceTier tier = tierRepository.save(new PriceTier(name, priceCents, event));
        tierSeatRepository.saveAll(seats.stream().map(seat -> new PriceTierSeat(tier, seat)).toList());
    }
}
