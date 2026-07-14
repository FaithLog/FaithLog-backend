DROP INDEX uk_campus_duty_assignments_active_coffee;

CREATE UNIQUE INDEX uk_campus_duty_assignments_active_coffee_user
    ON campus_duty_assignments (campus_id, duty_type, user_id)
    WHERE is_active = TRUE AND duty_type = 'COFFEE';
