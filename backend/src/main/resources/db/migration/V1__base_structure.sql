CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    tg_id BIGINT UNIQUE NOT NULL,
    tg_login TEXT
);

CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    name TEXT UNIQUE NOT NULL
);

CREATE TABLE events (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    registration_closes_at TIMESTAMPTZ NOT NULL,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    location TEXT NOT NULL,
    state VARCHAR(64) NOT NULL,
    icon TEXT,
    min_users INT NOT NULL,
    max_users INT NOT NULL,
    organizer_id UUID NOT NULL REFERENCES users(id)
);

CREATE INDEX idx_events_starts_at ON events(starts_at);
CREATE INDEX idx_events_organizer_id ON events(organizer_id);

CREATE TABLE event_users (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    event_id UUID NOT NULL REFERENCES events(id),
    user_id UUID NOT NULL REFERENCES users(id),

    CONSTRAINT uk_event_users_event_id_user_id UNIQUE (event_id, user_id)
);

CREATE INDEX idx_event_users_user_id ON event_users(user_id);

CREATE TABLE event_categories (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    event_id UUID NOT NULL REFERENCES events(id),
    category_id UUID NOT NULL REFERENCES categories(id),

    CONSTRAINT uk_event_categories_event_id_category_id UNIQUE (event_id, category_id)
);

CREATE INDEX idx_event_categories_category_id ON event_categories(category_id);
