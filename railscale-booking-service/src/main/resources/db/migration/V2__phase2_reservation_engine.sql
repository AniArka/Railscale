-- Phase 2: Reservation Engine Schema
-- Idempotency, Payments, Outbox Events, Processed Events, and Reservation Invariants

CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    response_status INTEGER,
    response_body TEXT,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP,
    CONSTRAINT uq_idempotency_user_key UNIQUE (user_id, idempotency_key),
    CONSTRAINT fk_idempotency_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_idempotency_lookup ON idempotency_keys(user_id, idempotency_key);

CREATE TABLE payments (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    amount NUMERIC(10,2) NOT NULL,
    provider_reference VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_payment_booking FOREIGN KEY (booking_id) REFERENCES bookings(id),
    CONSTRAINT uq_payment_booking UNIQUE (booking_id),
    CONSTRAINT chk_payment_amount CHECK (amount >= 0)
);

CREATE INDEX idx_payments_booking_id ON payments(booking_id);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    claimed_by UUID,
    claimed_until TIMESTAMP
);

CREATE INDEX idx_outbox_publishable ON outbox_events(created_at);
CREATE INDEX idx_outbox_claim_expiry ON outbox_events(claimed_until);

CREATE TABLE processed_events (
    consumer_name VARCHAR(100) NOT NULL,
    event_id UUID NOT NULL,
    processed_at TIMESTAMP NOT NULL,
    PRIMARY KEY (consumer_name, event_id)
);

CREATE INDEX idx_inventory_expired_holds ON seat_inventory(held_until);
CREATE INDEX idx_bookings_user_created ON bookings(user_id, created_at DESC);
