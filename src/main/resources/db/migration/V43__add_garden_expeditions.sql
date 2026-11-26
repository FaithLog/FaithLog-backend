CREATE TABLE garden_expeditions (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    id UUID NOT NULL UNIQUE,
    design_id VARCHAR(100) NOT NULL REFERENCES companion_designs(design_id),
    started_on DATE NOT NULL,
    ready_at TIMESTAMPTZ NOT NULL,
    claimed_at TIMESTAMPTZ
);
ALTER TABLE garden_expeditions ENABLE ROW LEVEL SECURITY;
