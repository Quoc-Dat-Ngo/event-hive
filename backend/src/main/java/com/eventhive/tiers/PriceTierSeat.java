package com.eventhive.tiers;

import java.util.UUID;

import com.eventhive.events.Event;
import com.eventhive.seats.Seat;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Assigns one venue seat to a price tier for an event (unique per event + seat). */
@Entity
@Table(name = "price_tier_seats")
@NoArgsConstructor
@Getter
@Setter
public class PriceTierSeat {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tier_id", nullable = false)
    private PriceTier tier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    public PriceTierSeat(PriceTier tier, Seat seat) {
        this.tier = tier;
        this.event = tier.getEvent();
        this.seat = seat;
    }
}
