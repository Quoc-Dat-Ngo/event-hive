package com.eventhive.entity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.eventhive.AbstractRepositoryTest;
import com.eventhive.events.Event;
import com.eventhive.events.EventRepository;
import com.eventhive.events.EventStatus;
import com.eventhive.events.VenueSummaryDTO;
import com.eventhive.venues.EventSummaryDTO;
import com.eventhive.venues.Venue;
import com.eventhive.venues.VenueRepository;

import static org.assertj.core.api.Assertions.*;

public class EventRepositoryTest extends AbstractRepositoryTest {
    @Autowired
    EventRepository eventRepository;

    @Autowired
    VenueRepository venueRepository;

    private Venue venue;

    @BeforeEach
    public void setUpNewVenue() {
        venue = venueRepository.save(new Venue("Accor Stadium", 100000, "Sydney"));
    }

    @AfterEach
    public void clearDatabase() {
        eventRepository.deleteAll();
        venueRepository.deleteAll();
    }

    @Test
    public void shouldReturnHostVenue() {
        Event e = eventRepository
                .save(new Event("Euniverse", "Park Eun Bin fan meeting", Instant.parse("2026-08-10T14:30:00+10:00"),
                        Instant.parse("2026-09-10T14:30:00+10:00"), "Park Eun Bin", EventStatus.PUBLISHED, venue));

        Optional<VenueSummaryDTO> summary = eventRepository.findHostVenueById(e.getId());

        assertThat(summary).hasValueSatisfying(dto -> {
            assertThat(dto.venueName()).isEqualTo("Accor Stadium");
            assertThat(dto.capacity()).isEqualTo(100000);
            assertThat(dto.location()).isEqualTo("Sydney");
        });
    }

    @Test
    public void shouldReturnAllAssociatedEvents() {
        Event e1 = eventRepository
                .save(new Event("Euniverse", "Park Eun Bin fan meeting", Instant.parse("2026-08-10T14:30:00+10:00"),
                        Instant.parse("2026-09-10T14:30:00+10:00"), "Park Eun Bin", EventStatus.PUBLISHED, venue));
        Event e2 = eventRepository.save(new Event("NewJeans Encore", null, Instant.parse("2026-08-10T14:30:00+10:00"),
                Instant.parse("2026-08-10T14:30:00+10:00"), null, EventStatus.DRAFT, venue));

        Event e3 = eventRepository
                .save(new Event("League of Legends World Final", null, Instant.parse("2026-08-10T14:30:00+10:00"),
                        Instant.parse("2026-08-10T14:30:00+10:00"), null, EventStatus.COMPLETED, venue));

        List<EventSummaryDTO> summary = eventRepository.findAllEventsAssociatedWithVenueId(venue.getId());

        assertThat(summary)
                .extracting("title", "purpose", "startsAt", "endsAt", "performer", "status")
                .contains(
                        tuple("Euniverse", "Park Eun Bin fan meeting", e1.getStartsAt(), e1.getEndsAt(), "Park Eun Bin",
                                EventStatus.PUBLISHED),
                        tuple("NewJeans Encore", null, e2.getStartsAt(), e2.getEndsAt(), null, EventStatus.DRAFT),
                        tuple("League of Legends World Final", null, e3.getStartsAt(), e3.getEndsAt(), null,
                                EventStatus.COMPLETED));
    }
}
