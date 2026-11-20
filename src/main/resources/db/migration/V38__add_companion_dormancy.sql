CREATE TABLE companion_garden_presence (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    last_visited_at TIMESTAMPTZ NOT NULL,
    dormant_at TIMESTAMPTZ
);
ALTER TABLE companion_garden_presence ENABLE ROW LEVEL SECURITY;
ALTER TABLE companion_instances ADD COLUMN last_awakened_at TIMESTAMPTZ;
