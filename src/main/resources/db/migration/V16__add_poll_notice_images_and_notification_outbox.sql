ALTER TABLE polls
    ADD COLUMN notice TEXT;

ALTER TABLE polls
    ADD CONSTRAINT ck_polls_notice_trimmed
        CHECK (notice IS NULL OR (notice = btrim(notice) AND char_length(notice) BETWEEN 1 AND 5000)),
    ADD CONSTRAINT uk_polls_campus_id_id UNIQUE (campus_id, id);

CREATE INDEX idx_polls_due_scheduled
    ON polls (starts_at, id)
    WHERE status = 'SCHEDULED';

CREATE TABLE poll_notification_outbox (
    id BIGSERIAL PRIMARY KEY,
    poll_id BIGINT NOT NULL,
    campus_id BIGINT NOT NULL,
    creator_id BIGINT,
    poll_type VARCHAR(40) NOT NULL,
    poll_title VARCHAR(200) NOT NULL,
    opened_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_poll_notification_outbox_poll
        FOREIGN KEY (campus_id, poll_id) REFERENCES polls (campus_id, id),
    CONSTRAINT fk_poll_notification_outbox_creator
        FOREIGN KEY (creator_id) REFERENCES users (id),
    CONSTRAINT uk_poll_notification_outbox_poll UNIQUE (poll_id),
    CONSTRAINT ck_poll_notification_outbox_poll_type
        CHECK (poll_type IN ('WED_SERVICE', 'SATURDAY_LEADER', 'COFFEE', 'MEAL', 'CUSTOM'))
);

CREATE INDEX idx_poll_notification_outbox_pending
    ON poll_notification_outbox (id)
    WHERE processed_at IS NULL;

CREATE TABLE poll_images (
    id BIGSERIAL PRIMARY KEY,
    campus_id BIGINT NOT NULL,
    poll_id BIGINT NOT NULL,
    media_asset_id BIGINT NOT NULL,
    display_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_poll_images_poll
        FOREIGN KEY (campus_id, poll_id) REFERENCES polls (campus_id, id),
    CONSTRAINT fk_poll_images_media_asset
        FOREIGN KEY (campus_id, media_asset_id) REFERENCES media_assets (campus_id, id),
    CONSTRAINT uk_poll_images_media_asset UNIQUE (media_asset_id),
    CONSTRAINT uk_poll_images_order UNIQUE (poll_id, display_order)
        DEFERRABLE INITIALLY IMMEDIATE,
    CONSTRAINT ck_poll_images_display_order CHECK (display_order >= 0)
);

CREATE INDEX idx_poll_images_poll_order
    ON poll_images (poll_id, display_order, id);

ALTER TABLE notification_logs
    DROP CONSTRAINT ck_notification_logs_type;

ALTER TABLE notification_logs
    ADD CONSTRAINT ck_notification_logs_type CHECK (
        notification_type IN (
            'DEVOTION_REMINDER',
            'DEVOTION_MISSING',
            'WED_POLL_OPEN',
            'WED_POLL_MISSING',
            'SATURDAY_POLL_OPEN',
            'SATURDAY_POLL_MISSING',
            'COFFEE_POLL_OPEN',
            'COFFEE_POLL_MISSING',
            'MEAL_POLL_OPEN',
            'CUSTOM_POLL_OPEN',
            'PAYMENT_UNPAID',
            'ANNOUNCEMENT_PUBLISHED',
            'CUSTOM'
        )
    );

ALTER TABLE poll_notification_outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE poll_images ENABLE ROW LEVEL SECURITY;
