ALTER TABLE users
    ADD COLUMN name TEXT NOT NULL,
    ADD COLUMN photo_url TEXT,
    ADD COLUMN city TEXT,
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD CONSTRAINT ck_users_name CHECK (char_length(name) BETWEEN 1 AND 100 AND name ~ '\S'),
    ADD CONSTRAINT ck_users_city CHECK (city IS NULL OR (char_length(city) BETWEEN 1 AND 100 AND city ~ '\S'));

CREATE TABLE user_categories (
    user_id UUID NOT NULL REFERENCES users(id),
    category_id UUID NOT NULL REFERENCES categories(id),

    PRIMARY KEY (user_id, category_id)
);

ALTER TABLE events
    ADD COLUMN participants_count INT NOT NULL,
    ADD COLUMN latitude DOUBLE PRECISION,
    ADD COLUMN longitude DOUBLE PRECISION,
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD CONSTRAINT ck_events_title CHECK (char_length(title) BETWEEN 1 AND 100 AND title ~ '\S'),
    ADD CONSTRAINT ck_events_description CHECK (char_length(description) <= 2000),
    ADD CONSTRAINT ck_events_location CHECK (location ~ '\S'),
    ADD CONSTRAINT ck_events_limits CHECK (1 <= min_users AND min_users <= max_users AND max_users <= 50),
    ADD CONSTRAINT ck_events_participants_count CHECK (participants_count BETWEEN 1 AND max_users),
    ADD CONSTRAINT ck_events_point CHECK ((latitude IS NULL) = (longitude IS NULL)),
    ADD CONSTRAINT ck_events_latitude CHECK (latitude BETWEEN -90 AND 90),
    ADD CONSTRAINT ck_events_longitude CHECK (longitude BETWEEN -180 AND 180),
    ADD CONSTRAINT ck_events_dates CHECK (registration_closes_at <= starts_at AND starts_at < ends_at),
    ADD CONSTRAINT ck_events_state CHECK (
        state IN ('recruiting', 'cancelled_by_organizer', 'cancelled_by_system', 'registration_closed', 'finished')
    );

CREATE INDEX idx_events_recruiting_deadline ON events(registration_closes_at) WHERE state = 'recruiting';
CREATE INDEX idx_events_closed_end ON events(ends_at) WHERE state = 'registration_closed';

ALTER TABLE event_users
    ADD COLUMN joined_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE TABLE comments (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    event_id UUID NOT NULL REFERENCES events(id),
    author_id UUID NOT NULL REFERENCES users(id),
    text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_comments_text CHECK (char_length(text) BETWEEN 1 AND 1000 AND text ~ '\S')
);

CREATE INDEX idx_comments_event_id_created_at ON comments(event_id, created_at);

INSERT INTO categories (name) VALUES
    ('Выставки'),
    ('Кафе'),
    ('Квесты'),
    ('Кино'),
    ('Концерты'),
    ('Настолки'),
    ('Прогулки'),
    ('Спорт'),
    ('Театр'),
    ('Учёба');
