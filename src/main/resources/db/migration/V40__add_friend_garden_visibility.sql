CREATE TABLE friend_garden_visibility (
    campus_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    membership_joined_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    PRIMARY KEY (campus_id, user_id),
    FOREIGN KEY (campus_id, user_id) REFERENCES campus_members(campus_id, user_id) ON DELETE CASCADE
);
