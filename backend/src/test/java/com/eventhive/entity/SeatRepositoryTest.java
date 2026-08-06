package com.eventhive.entity;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.eventhive.AbstractRepositoryTest;
import com.eventhive.events.VenueSummaryDTO;
import com.eventhive.seats.Seat;
import com.eventhive.seats.SeatRepository;
import com.eventhive.venues.SeatSummaryDTO;
import com.eventhive.venues.Venue;
import com.eventhive.venues.VenueRepository;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Optional;

public class SeatRepositoryTest extends AbstractRepositoryTest {
    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private VenueRepository venueRepository;

    private Venue v;

    @BeforeEach
    public void setUpNewVenue() {
        v = new Venue("Accor Stadium", 100000, "Sydney");
        venueRepository.save(v);
        seatRepository.save(new Seat("A", 1, v));
        seatRepository.save(new Seat("B", 2, v));
        seatRepository.save(new Seat("C", 3, v));
        seatRepository.save(new Seat("D", 4, v));
    }

    @AfterEach
    public void clearDatabase() {
        seatRepository.deleteAll();
        venueRepository.deleteAll();
    }

    @Test
    public void shouldReturnHostVenue() {
        Seat s = new Seat("AA", 50, v);
        seatRepository.save(s);

        Optional<VenueSummaryDTO> summary = seatRepository.findHostVenueById(s.getId());
        assertThat(summary).hasValueSatisfying(dto -> {
            assertThat(dto.venueName()).isEqualTo("Accor Stadium");
            assertThat(dto.capacity()).isEqualTo(100000);
            assertThat(dto.location()).isEqualTo("Sydney");
        });
    }

    @Test
    public void shouldReturnListOfAllSeats() {
        List<SeatSummaryDTO> summary = seatRepository.findAllSeatsAssociatedWithVenueId(v.getId());

        assertThat(summary)
                .extracting("seatRow", "number")
                .contains(
                        tuple("A", 1),
                        tuple("B", 2),
                        tuple("C", 3),
                        tuple("D", 4));

    }

}
