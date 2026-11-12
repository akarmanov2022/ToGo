ALTER TABLE notifications DROP CONSTRAINT ck_notifications_kind;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_kind CHECK (
    kind IN ('joined', 'confirmed', 'cancelled', 'reminder_day', 'reminder_hour', 'comment', 'rating')
);
