CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE stations (
    id UUID PRIMARY KEY,
    code VARCHAR(10) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    city VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE trains (
    id UUID PRIMARY KEY,
    train_number VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    total_coaches INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE coaches (
    id UUID PRIMARY KEY,
    train_id UUID NOT NULL,
    coach_number VARCHAR(10) NOT NULL,
    coach_type VARCHAR(30) NOT NULL,
    total_seats INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_coach_train FOREIGN KEY (train_id) REFERENCES trains(id) ON DELETE CASCADE,
    CONSTRAINT uq_train_coach UNIQUE (train_id, coach_number)
);

CREATE TABLE seats (
    id UUID PRIMARY KEY,
    coach_id UUID NOT NULL,
    seat_number VARCHAR(10) NOT NULL,
    seat_type VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_seat_coach FOREIGN KEY (coach_id) REFERENCES coaches(id) ON DELETE CASCADE,
    CONSTRAINT uq_coach_seat UNIQUE (coach_id, seat_number)
);

CREATE TABLE journeys (
    id UUID PRIMARY KEY,
    train_id UUID NOT NULL,
    source_station_id UUID NOT NULL,
    destination_station_id UUID NOT NULL,
    departure_time TIMESTAMP NOT NULL,
    arrival_time TIMESTAMP NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_journey_train FOREIGN KEY (train_id) REFERENCES trains(id),
    CONSTRAINT fk_journey_source FOREIGN KEY (source_station_id) REFERENCES stations(id),
    CONSTRAINT fk_journey_dest FOREIGN KEY (destination_station_id) REFERENCES stations(id)
);

CREATE TABLE seat_inventory (
    id UUID PRIMARY KEY,
    journey_id UUID NOT NULL,
    seat_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    held_by UUID,
    held_until TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_inventory_journey FOREIGN KEY (journey_id) REFERENCES journeys(id) ON DELETE CASCADE,
    CONSTRAINT fk_inventory_seat FOREIGN KEY (seat_id) REFERENCES seats(id),
    CONSTRAINT uq_journey_seat UNIQUE (journey_id, seat_id)
);

CREATE TABLE bookings (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    journey_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    total_amount NUMERIC(10,2) NOT NULL,
    payment_initiated_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_booking_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_booking_journey FOREIGN KEY (journey_id) REFERENCES journeys(id)
);

CREATE TABLE booking_items (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL,
    inventory_id UUID NOT NULL,
    price NUMERIC(10,2) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE,
    CONSTRAINT fk_inventory FOREIGN KEY (inventory_id) REFERENCES seat_inventory(id)
);

CREATE INDEX idx_coaches_train_id ON coaches(train_id);
CREATE INDEX idx_seats_coach_id ON seats(coach_id);
CREATE INDEX idx_journeys_search ON journeys(source_station_id, destination_station_id, departure_time);
CREATE INDEX idx_seat_inventory_journey ON seat_inventory(journey_id);
CREATE INDEX idx_seat_inventory_status ON seat_inventory(journey_id, status);
CREATE INDEX idx_bookings_user_id ON bookings(user_id);
CREATE INDEX idx_bookings_journey_id ON bookings(journey_id);
CREATE INDEX idx_booking_items_booking_id ON booking_items(booking_id);
