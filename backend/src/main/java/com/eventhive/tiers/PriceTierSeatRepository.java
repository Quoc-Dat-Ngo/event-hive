package com.eventhive.tiers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PriceTierSeatRepository extends JpaRepository<PriceTierSeat, UUID> {
    @Query("""
            SELECT NEW com.eventhive.tiers.TierSeatAssignment(ts.seat.id, ts.tier.id)
            FROM PriceTierSeat ts
            WHERE ts.event.id = ?1
            """)
    List<TierSeatAssignment> findAssignmentsByEventId(UUID eventId);

    @Query("""
            SELECT ts.tier
            FROM PriceTierSeat ts
            WHERE ts.event.id = ?1 AND ts.seat.id = ?2
            """)
    Optional<PriceTier> findTierForSeat(UUID eventId, UUID seatId);

    @Query("""
            SELECT ts.seat.id
            FROM PriceTierSeat ts
            WHERE ts.tier.id = ?1
            """)
    List<UUID> findSeatIdsByTierId(UUID tierId);

    long countByTierId(UUID tierId);

    @Modifying
    @Query("DELETE FROM PriceTierSeat ts WHERE ts.tier.id = ?1")
    void deleteAllByTierId(UUID tierId);
}
