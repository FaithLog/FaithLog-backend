CREATE TABLE campus_garden_goal_contributions (
    campus_id BIGINT NOT NULL REFERENCES campuses(id) ON DELETE CASCADE,
    week_start DATE NOT NULL,
    helper_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (campus_id, week_start, helper_id)
);
