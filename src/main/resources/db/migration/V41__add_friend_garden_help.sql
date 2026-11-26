CREATE TABLE friend_garden_help (
    helper_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    help_date DATE NOT NULL,
    animal_id UUID NOT NULL REFERENCES companion_instances(id) ON DELETE CASCADE,
    PRIMARY KEY (helper_id, help_date),
    UNIQUE (animal_id, help_date)
);
