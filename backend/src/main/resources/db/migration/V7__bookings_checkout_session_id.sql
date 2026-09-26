-- Lets a customer resume the Stripe Checkout Session of their own PENDING booking,
-- and lets cancellation expire that session on Stripe's side.
ALTER TABLE bookings
ADD COLUMN checkout_session_id VARCHAR(255);
