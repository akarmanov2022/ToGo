DROP TABLE event_categories;
DROP TABLE user_categories;
DROP TABLE categories;

CREATE TABLE user_categories (
    user_id UUID NOT NULL REFERENCES users(id),
    category TEXT NOT NULL,

    PRIMARY KEY (user_id, category),
    CONSTRAINT ck_user_categories_category CHECK (
        category IN ('PICNIC', 'RUN', 'EXCURSION', 'BOARD_GAMES', 'QUEST', 'CINEMA', 'CAFE')
    )
);

ALTER TABLE events
    ADD COLUMN category TEXT NOT NULL,
    ADD CONSTRAINT ck_events_category CHECK (
        category IN ('PICNIC', 'RUN', 'EXCURSION', 'BOARD_GAMES', 'QUEST', 'CINEMA', 'CAFE')
    ),
    DROP CONSTRAINT ck_events_description,
    ADD CONSTRAINT ck_events_description CHECK (char_length(description) BETWEEN 1 AND 500 AND description ~ '\S');

CREATE INDEX idx_events_category ON events(category);

CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    user_id UUID NOT NULL REFERENCES users(id),
    event_id UUID NOT NULL REFERENCES events(id),
    kind TEXT NOT NULL,
    due_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    attempts INT NOT NULL DEFAULT 0,

    CONSTRAINT uk_notifications_user_id_event_id_kind UNIQUE (user_id, event_id, kind),
    CONSTRAINT ck_notifications_kind CHECK (
        kind IN ('joined', 'confirmed', 'cancelled', 'reminder_day', 'reminder_hour')
    ),
    CONSTRAINT ck_notifications_attempts CHECK (attempts >= 0)
);

CREATE INDEX idx_notifications_pending_due ON notifications(due_at) WHERE sent_at IS NULL;
