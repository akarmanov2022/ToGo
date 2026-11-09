ALTER TABLE events
    ADD CONSTRAINT ck_events_users_limits CHECK (1 <= min_users AND min_users <= max_users),
    ADD CONSTRAINT ck_events_registration_before_start CHECK (registration_closes_at <= starts_at),
    ADD CONSTRAINT ck_events_start_before_end CHECK (starts_at < ends_at),
    ADD CONSTRAINT ck_events_state CHECK (
        state IN ('recruiting', 'registration_closed', 'cancelled_by_organizer', 'cancelled_by_system', 'finished')
    );

CREATE INDEX idx_events_recruiting_registration_closes_at ON events(registration_closes_at) WHERE state = 'recruiting';
CREATE INDEX idx_events_registration_closed_ends_at ON events(ends_at) WHERE state = 'registration_closed';

INSERT INTO categories (name)
VALUES ('Кафе'), ('Спорт'), ('Выставки'), ('Кино'), ('Настолки'), ('Прогулки'), ('Музыка'), ('Обучение');
