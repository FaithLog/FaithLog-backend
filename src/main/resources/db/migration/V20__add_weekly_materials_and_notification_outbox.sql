CREATE TABLE weekly_materials (
    id BIGSERIAL PRIMARY KEY,
    campus_id BIGINT NOT NULL,
    week_start_date DATE NOT NULL,
    material_type VARCHAR(30) NOT NULL,
    media_asset_id BIGINT NOT NULL,
    uploaded_by BIGINT NOT NULL,
    status VARCHAR(10) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_weekly_materials_campus_id_id UNIQUE (campus_id, id),
    CONSTRAINT uk_weekly_materials_slot UNIQUE (campus_id, week_start_date, material_type),
    CONSTRAINT uk_weekly_materials_media_asset UNIQUE (media_asset_id),
    CONSTRAINT fk_weekly_materials_campus
        FOREIGN KEY (campus_id) REFERENCES campuses (id),
    CONSTRAINT fk_weekly_materials_media_asset
        FOREIGN KEY (campus_id, media_asset_id) REFERENCES media_assets (campus_id, id),
    CONSTRAINT fk_weekly_materials_uploaded_by
        FOREIGN KEY (uploaded_by) REFERENCES users (id),
    CONSTRAINT ck_weekly_materials_monday
        CHECK (EXTRACT(ISODOW FROM week_start_date) = 1),
    CONSTRAINT ck_weekly_materials_type
        CHECK (material_type IN ('SHEPHERD_GUIDE', 'SHARING_SHEET')),
    CONSTRAINT ck_weekly_materials_status
        CHECK (status IN ('ACTIVE', 'DELETED'))
);

CREATE INDEX idx_weekly_materials_campus_week
    ON weekly_materials (campus_id, week_start_date DESC, id DESC);

CREATE INDEX idx_weekly_materials_active_slot
    ON weekly_materials (campus_id, week_start_date, material_type, id)
    WHERE status = 'ACTIVE';

CREATE TABLE weekly_material_notification_outbox (
    id BIGSERIAL PRIMARY KEY,
    campus_id BIGINT NOT NULL,
    weekly_material_id BIGINT NOT NULL,
    week_start_date DATE NOT NULL,
    material_type VARCHAR(30) NOT NULL,
    uploader_id BIGINT NOT NULL,
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_weekly_material_outbox_slot
        UNIQUE (campus_id, week_start_date, material_type),
    CONSTRAINT fk_weekly_material_outbox_material
        FOREIGN KEY (campus_id, weekly_material_id)
        REFERENCES weekly_materials (campus_id, id),
    CONSTRAINT fk_weekly_material_outbox_uploader
        FOREIGN KEY (uploader_id) REFERENCES users (id),
    CONSTRAINT ck_weekly_material_outbox_monday
        CHECK (EXTRACT(ISODOW FROM week_start_date) = 1),
    CONSTRAINT ck_weekly_material_outbox_type
        CHECK (material_type = 'SHARING_SHEET')
);

CREATE INDEX idx_weekly_material_outbox_pending
    ON weekly_material_notification_outbox (processed_at, id);

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
            'WEEKLY_SHARING_SHEET_PUBLISHED', 'CUSTOM'
        )
    );

ALTER TABLE weekly_materials ENABLE ROW LEVEL SECURITY;
ALTER TABLE weekly_material_notification_outbox ENABLE ROW LEVEL SECURITY;
