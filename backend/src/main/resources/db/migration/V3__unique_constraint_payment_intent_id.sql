ALTER TABLE payments
ADD CONSTRAINT unique_payment_intent_id UNIQUE (stripe_payment_intent_id);