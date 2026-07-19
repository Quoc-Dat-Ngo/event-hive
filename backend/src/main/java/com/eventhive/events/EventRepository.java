package com.eventhive.events;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface EventRepository extends JpaRepository<Event, UUID> {
    @Modifying(clearAutomatically = true)
    @Query("""
            SELECT new com.eventhive.events.VenueSummaryDTO(v.name, v.location, v.capacity)
            FROM Event e JOIN e.venue v
            WHERE e.id = ?1
                """)
    Optional<VenueSummaryDTO> findHostVenueById(UUID id);
}
