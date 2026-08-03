CREATE TABLE announcement_categories (
    id BIGSERIAL PRIMARY KEY,
    campus_id BIGINT NOT NULL,
    name VARCHAR(30) NOT NULL,
    color VARCHAR(7) NOT NULL,
    display_order INTEGER NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_announcement_categories_campus
        FOREIGN KEY (campus_id) REFERENCES campuses (id),
    CONSTRAINT uk_announcement_categories_campus_id_id UNIQUE (campus_id, id),
    CONSTRAINT ck_announcement_categories_name_trimmed
        CHECK (name = btrim(name) AND char_length(name) BETWEEN 1 AND 30),
    CONSTRAINT ck_announcement_categories_color
        CHECK (color ~ '^#[0-9A-F]{6}$'),
    CONSTRAINT ck_announcement_categories_display_order
        CHECK (display_order >= 0)
);

CREATE UNIQUE INDEX uk_announcement_categories_campus_lower_name
    ON announcement_categories (campus_id, lower(name));

CREATE INDEX idx_announcement_categories_campus_display
    ON announcement_categories (campus_id, display_order, id);

INSERT INTO announcement_categories (campus_id, name, color, display_order, is_active)
SELECT id, '일반', '#3B82F6', 0, TRUE
FROM campuses;

CREATE TABLE announcements (
    id BIGSERIAL PRIMARY KEY,
    campus_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,
    is_pinned BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(30) NOT NULL,
    publish_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_announcements_campus
        FOREIGN KEY (campus_id) REFERENCES campuses (id),
    CONSTRAINT fk_announcements_category
        FOREIGN KEY (campus_id, category_id) REFERENCES announcement_categories (campus_id, id),
    CONSTRAINT fk_announcements_author
        FOREIGN KEY (author_id) REFERENCES users (id),
    CONSTRAINT uk_announcements_campus_id_id UNIQUE (campus_id, id),
    CONSTRAINT ck_announcements_title_trimmed
        CHECK (title = btrim(title) AND char_length(title) BETWEEN 1 AND 100),
    CONSTRAINT ck_announcements_content_trimmed
        CHECK (content = btrim(content) AND char_length(content) BETWEEN 1 AND 5000),
    CONSTRAINT ck_announcements_status
        CHECK (status IN ('SCHEDULED', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT ck_announcements_publication_state
        CHECK ((status = 'SCHEDULED' AND published_at IS NULL) OR status IN ('PUBLISHED', 'ARCHIVED'))
);

CREATE INDEX idx_announcements_campus_status_pinned_published
    ON announcements (campus_id, status, is_pinned DESC, published_at DESC, id DESC);

CREATE INDEX idx_announcements_due_scheduled
    ON announcements (publish_at, id)
    WHERE status = 'SCHEDULED';

CREATE TABLE announcement_notification_outbox (
    id BIGSERIAL PRIMARY KEY,
    announcement_id BIGINT NOT NULL,
    campus_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    category_name VARCHAR(30) NOT NULL,
    announcement_title VARCHAR(100) NOT NULL,
    published_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_announcement_notification_outbox_announcement
        FOREIGN KEY (campus_id, announcement_id) REFERENCES announcements (campus_id, id),
    CONSTRAINT fk_announcement_notification_outbox_category
        FOREIGN KEY (campus_id, category_id) REFERENCES announcement_categories (campus_id, id),
    CONSTRAINT fk_announcement_notification_outbox_author
        FOREIGN KEY (author_id) REFERENCES users (id),
    CONSTRAINT uk_announcement_notification_outbox_announcement UNIQUE (announcement_id)
);

CREATE INDEX idx_announcement_notification_outbox_pending
    ON announcement_notification_outbox (id)
    WHERE processed_at IS NULL;

CREATE TABLE media_assets (
    id BIGSERIAL PRIMARY KEY,
    campus_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    input_content_type VARCHAR(20) NOT NULL,
    input_byte_size BIGINT NOT NULL,
    expected_sha256 VARCHAR(64) NOT NULL,
    temporary_object_key VARCHAR(200),
    thumbnail_object_key VARCHAR(200),
    detail_object_key VARCHAR(200),
    output_sha256 VARCHAR(64),
    width INTEGER,
    height INTEGER,
    output_byte_size BIGINT,
    status VARCHAR(20) NOT NULL,
    failure_reason VARCHAR(100),
    orphaned_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_media_assets_campus
        FOREIGN KEY (campus_id) REFERENCES campuses (id),
    CONSTRAINT fk_media_assets_owner
        FOREIGN KEY (owner_user_id) REFERENCES users (id),
    CONSTRAINT uk_media_assets_campus_id_id UNIQUE (campus_id, id),
    CONSTRAINT uk_media_assets_temporary_object_key UNIQUE (temporary_object_key),
    CONSTRAINT uk_media_assets_thumbnail_object_key UNIQUE (thumbnail_object_key),
    CONSTRAINT uk_media_assets_detail_object_key UNIQUE (detail_object_key),
    CONSTRAINT ck_media_assets_input_content_type
        CHECK (input_content_type IN ('image/jpeg', 'image/png')),
    CONSTRAINT ck_media_assets_input_byte_size
        CHECK (input_byte_size BETWEEN 1 AND 5242880),
    CONSTRAINT ck_media_assets_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED', 'ORPHANED')),
    CONSTRAINT ck_media_assets_dimensions
        CHECK ((width IS NULL AND height IS NULL) OR (width BETWEEN 1 AND 4096 AND height BETWEEN 1 AND 4096))
);

CREATE INDEX idx_media_assets_temporary_cleanup
    ON media_assets (expires_at, id)
    WHERE status IN ('PENDING', 'FAILED')
       OR (status = 'READY' AND temporary_object_key IS NOT NULL);

CREATE INDEX idx_media_assets_orphan_cleanup
    ON media_assets (orphaned_at, id)
    WHERE status = 'ORPHANED';

CREATE TABLE announcement_images (
    id BIGSERIAL PRIMARY KEY,
    campus_id BIGINT NOT NULL,
    announcement_id BIGINT NOT NULL,
    media_asset_id BIGINT NOT NULL,
    display_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_announcement_images_announcement
        FOREIGN KEY (campus_id, announcement_id) REFERENCES announcements (campus_id, id),
    CONSTRAINT fk_announcement_images_media_asset
        FOREIGN KEY (campus_id, media_asset_id) REFERENCES media_assets (campus_id, id),
    CONSTRAINT uk_announcement_images_media_asset UNIQUE (media_asset_id),
    CONSTRAINT uk_announcement_images_order UNIQUE (announcement_id, display_order)
        DEFERRABLE INITIALLY IMMEDIATE,
    CONSTRAINT ck_announcement_images_display_order CHECK (display_order >= 0)
);

CREATE INDEX idx_announcement_images_announcement_order
    ON announcement_images (announcement_id, display_order, id);

ALTER TABLE notification_logs
    ADD COLUMN data_payload TEXT NOT NULL DEFAULT '{}';

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
            'PAYMENT_UNPAID',
            'ANNOUNCEMENT_PUBLISHED',
            'CUSTOM'
        )
    );

ALTER TABLE announcement_categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE announcements ENABLE ROW LEVEL SECURITY;
ALTER TABLE announcement_notification_outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE media_assets ENABLE ROW LEVEL SECURITY;
ALTER TABLE announcement_images ENABLE ROW LEVEL SECURITY;
