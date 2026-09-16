ALTER TABLE bookings
DROP CONSTRAINT bookings_seat_id_event_id_key;

CREATE UNIQUE INDEX booking_event_seat_active_unique
ON bookings (event_id, seat_id)
WHERE status in ('PENDING', 'CONFIRMED');