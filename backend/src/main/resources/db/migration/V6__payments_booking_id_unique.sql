ALTER TABLE payments
ADD CONSTRAINT unique_payment_booking_id UNIQUE (booking_id);