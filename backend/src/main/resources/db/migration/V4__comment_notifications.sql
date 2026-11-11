ALTER TABLE notifications ADD COLUMN comment_id UUID REFERENCES comments(id);
ALTER TABLE notifications DROP CONSTRAINT uk_notifications_user_id_event_id_kind;
ALTER TABLE notifications DROP CONSTRAINT ck_notifications_kind;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_kind CHECK (
    kind IN ('joined', 'confirmed', 'cancelled', 'reminder_day', 'reminder_hour', 'comment')
);
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_comment_id CHECK (
    (kind = 'comment') = (comment_id IS NOT NULL)
);
CREATE UNIQUE INDEX uk_notifications_event_kind_once
    ON notifications(user_id, event_id, kind) WHERE comment_id IS NULL;
CREATE UNIQUE INDEX uk_notifications_comment_once
    ON notifications(user_id, comment_id) WHERE comment_id IS NOT NULL;
