-- Organiser-defined price options for an event, each covering a set of the venue's seats
CREATE TABLE price_tiers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    price_cents INTEGER NOT NULL CHECK (price_cents > 0),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now(),

    UNIQUE (event_id, name)
);

CREATE TABLE price_tier_seats (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tier_id UUID NOT NULL REFERENCES price_tiers(id) ON DELETE CASCADE,
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    seat_id UUID NOT NULL REFERENCES seats(id) ON DELETE CASCADE,

    -- A seat has exactly one price per event, so tiers can never overlap
    UNIQUE (event_id, seat_id)
);

CREATE INDEX idx_price_tiers_event_id ON price_tiers(event_id);
CREATE INDEX idx_price_tier_seats_tier_id ON price_tier_seats(tier_id);
