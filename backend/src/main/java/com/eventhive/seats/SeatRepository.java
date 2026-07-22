package com.eventhive.seats;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.eventhive.events.VenueSummaryDTO;
import com.eventhive.venues.SeatSummaryDTO;

public interface SeatRepository extends JpaRepository<Seat, UUID>, JpaSpecificationExecutor<Seat> {
    @Query("""
            SELECT new com.eventhive.events.VenueSummaryDTO(v.name, v.location, v.capacity)
            FROM Seat s
            JOIN s.venue v
            WHERE s.id = ?1
                """)
    Optional<VenueSummaryDTO> findHostVenueById(UUID id);

    @Query("""
                SELECT new com.eventhive.venues.SeatSummaryDTO(s.id, s.seatRow, s.number)
                FROM Seat s
                WHERE s.venue.id = ?1
            """)
    List<SeatSummaryDTO> findAllSeatsAssociatedWithVenueId(UUID id);
}
