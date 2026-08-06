ALTER TABLE weekly_materials
    ADD COLUMN scope_campus_id BIGINT;

UPDATE weekly_materials
SET scope_campus_id = media_campus_id
WHERE material_type = 'SHEPHERD_GUIDE';

ALTER TABLE weekly_materials
    DROP CONSTRAINT uk_weekly_materials_slot,
    ADD CONSTRAINT fk_weekly_materials_scope_campus
        FOREIGN KEY (scope_campus_id) REFERENCES campuses (id),
    ADD CONSTRAINT ck_weekly_materials_scope CHECK (
        (material_type = 'SHEPHERD_GUIDE' AND scope_campus_id IS NOT NULL)
        OR
        (material_type IN ('SUNDAY_SHARING_SHEET', 'SATURDAY_LEADER_SHARING_SHEET')
            AND scope_campus_id IS NULL)
    );

CREATE UNIQUE INDEX uk_weekly_materials_shepherd_slot
    ON weekly_materials (scope_campus_id, week_start_date, material_type)
    WHERE material_type = 'SHEPHERD_GUIDE';

CREATE UNIQUE INDEX uk_weekly_materials_global_sheet_slot
    ON weekly_materials (week_start_date, material_type)
    WHERE material_type IN ('SUNDAY_SHARING_SHEET', 'SATURDAY_LEADER_SHARING_SHEET');

CREATE INDEX idx_weekly_materials_shepherd_active_scope
    ON weekly_materials (scope_campus_id, week_start_date DESC, id DESC)
    WHERE material_type = 'SHEPHERD_GUIDE' AND status = 'ACTIVE';
