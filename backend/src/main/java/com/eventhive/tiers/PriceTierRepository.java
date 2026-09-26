package com.eventhive.tiers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceTierRepository extends JpaRepository<PriceTier, UUID> {
    List<PriceTier> findByEventIdOrderByPriceCentsDesc(UUID eventId);

    Optional<PriceTier> findByIdAndEventId(UUID id, UUID eventId);

    boolean existsByEventIdAndNameIgnoreCase(UUID eventId, String name);
}
