package com.eventhive.events;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.eventhive.venues.EventSummaryDTO;

public interface EventRepository extends JpaRepository<Event, UUID> {
    @Query("""
            SELECT new com.eventhive.events.VenueSummaryDTO(v.name, v.location, v.capacity)
            FROM Event e
            JOIN e.venue v
            WHERE e.id = ?1
                """)
    Optional<VenueSummaryDTO> findHostVenueById(UUID id);

    @Query("""
                SELECT new com.eventhive.venues.EventSummaryDTO(e.id, e.title, e.purpose, e.startsAt, e.endsAt, e.performer, e.status)
                FROM Event e
                WHERE e.venue.id = ?1
            """)
    List<EventSummaryDTO> findAllEventsAssociatedWithVenueId(UUID id);
}
