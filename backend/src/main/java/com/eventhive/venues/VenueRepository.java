package com.eventhive.venues;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VenueRepository extends JpaRepository<Venue, UUID> {

}
