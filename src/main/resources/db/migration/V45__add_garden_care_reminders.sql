CREATE TABLE garden_care_reminders (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    last_queued_on DATE
);
ALTER TABLE garden_care_reminders ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification_logs
    DROP CONSTRAINT ck_notification_logs_type;

ALTER TABLE notification_logs
    ADD CONSTRAINT ck_notification_logs_type CHECK (
        notification_type IN (
            'DEVOTION_REMINDER', 'DEVOTION_MISSING',
            'WED_POLL_OPEN', 'WED_POLL_MISSING',
            'SATURDAY_POLL_OPEN', 'SATURDAY_POLL_MISSING',
            'COFFEE_POLL_OPEN', 'COFFEE_POLL_MISSING',
            'MEAL_POLL_OPEN', 'CUSTOM_POLL_OPEN',
            'PAYMENT_UNPAID', 'ANNOUNCEMENT_PUBLISHED',
            'WEEKLY_SHARING_SHEET_PUBLISHED', 'GARDEN_CARE_REMINDER', 'CUSTOM'
        )
    );

