CREATE TABLE event_ratings (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    event_id UUID NOT NULL REFERENCES events(id),
    user_id UUID NOT NULL REFERENCES users(id),
    score SMALLINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uk_event_ratings_event_id_user_id UNIQUE (event_id, user_id),
    CONSTRAINT ck_event_ratings_score CHECK (score BETWEEN 1 AND 5)
);
