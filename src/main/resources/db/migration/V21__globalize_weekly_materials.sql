DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM weekly_materials
        GROUP BY week_start_date, material_type
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'duplicate weekly material global slot'
            USING ERRCODE = '23505';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM weekly_material_notification_outbox
        GROUP BY week_start_date, material_type
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'duplicate weekly material outbox global slot'
            USING ERRCODE = '23505';
    END IF;
END $$;

ALTER TABLE weekly_materials
    DROP CONSTRAINT fk_weekly_materials_media_asset,
    DROP CONSTRAINT fk_weekly_materials_campus,
    DROP CONSTRAINT uk_weekly_materials_campus_id_id,
    DROP CONSTRAINT uk_weekly_materials_slot,
    DROP CONSTRAINT ck_weekly_materials_type;

DROP INDEX idx_weekly_materials_campus_week;
DROP INDEX idx_weekly_materials_active_slot;

ALTER TABLE weekly_materials RENAME COLUMN campus_id TO media_campus_id;

UPDATE weekly_materials
SET material_type = 'SUNDAY_SHARING_SHEET'
WHERE material_type = 'SHARING_SHEET';

ALTER TABLE weekly_materials
    ADD CONSTRAINT uk_weekly_materials_slot UNIQUE (week_start_date, material_type),
    ADD CONSTRAINT fk_weekly_materials_media_campus
        FOREIGN KEY (media_campus_id) REFERENCES campuses (id),
    ADD CONSTRAINT fk_weekly_materials_media_asset
        FOREIGN KEY (media_campus_id, media_asset_id) REFERENCES media_assets (campus_id, id),
    ADD CONSTRAINT ck_weekly_materials_type CHECK (
        material_type IN (
            'SHEPHERD_GUIDE',
            'SUNDAY_SHARING_SHEET',
            'SATURDAY_LEADER_SHARING_SHEET'
        )
    );

CREATE INDEX idx_weekly_materials_week
    ON weekly_materials (week_start_date DESC, id DESC);

CREATE INDEX idx_weekly_materials_active_slot
    ON weekly_materials (week_start_date, material_type, id)
    WHERE status = 'ACTIVE';

ALTER TABLE weekly_material_notification_outbox
    DROP CONSTRAINT uk_weekly_material_outbox_slot,
    DROP CONSTRAINT ck_weekly_material_outbox_type;

UPDATE weekly_material_notification_outbox
SET material_type = 'SUNDAY_SHARING_SHEET'
WHERE material_type = 'SHARING_SHEET';

ALTER TABLE weekly_material_notification_outbox
    ADD CONSTRAINT uk_weekly_material_outbox_slot UNIQUE (week_start_date, material_type),
    ADD CONSTRAINT ck_weekly_material_outbox_type
        CHECK (material_type = 'SUNDAY_SHARING_SHEET');

CREATE TABLE weekly_material_global_lock (
    id SMALLINT PRIMARY KEY,
    CONSTRAINT ck_weekly_material_global_lock_singleton CHECK (id = 1)
);

INSERT INTO weekly_material_global_lock (id) VALUES (1);

ALTER TABLE weekly_material_global_lock ENABLE ROW LEVEL SECURITY;
