CREATE INDEX idx_charge_items_campus_category_source
    ON charge_items (campus_id, payment_category, source_type, source_id);

CREATE INDEX idx_charge_items_campus_category_status_user
    ON charge_items (campus_id, payment_category, status, user_id);

CREATE INDEX idx_campus_members_user_id_id
    ON campus_members (user_id, id);
