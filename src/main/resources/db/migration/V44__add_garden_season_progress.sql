CREATE TABLE garden_season_progress (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    season_start DATE NOT NULL,
    completed INTEGER NOT NULL CHECK (completed >= 0),
    claimed BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (user_id, season_start)
);
ALTER TABLE garden_season_progress ENABLE ROW LEVEL SECURITY;
