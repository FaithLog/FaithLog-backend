CREATE TABLE user_gardens (
 user_id BIGINT PRIMARY KEY REFERENCES users(id),
 background_id VARCHAR(40),
 version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
 updated_at TIMESTAMPTZ NOT NULL,
 FOREIGN KEY(user_id,background_id) REFERENCES user_backgrounds(user_id,background_id)
);
CREATE TABLE garden_placements (
 user_id BIGINT NOT NULL REFERENCES user_gardens(user_id),
 slot SMALLINT NOT NULL CHECK(slot BETWEEN 1 AND 5),
 design_id VARCHAR(100) NOT NULL,
 x DOUBLE PRECISION NOT NULL CHECK(x>=0 AND x<=1),
 y DOUBLE PRECISION NOT NULL CHECK(y>=0 AND y<=1),
 PRIMARY KEY(user_id,slot),UNIQUE(user_id,design_id),
 FOREIGN KEY(user_id,design_id) REFERENCES user_companions(user_id,design_id)
);
CREATE INDEX ix_garden_placements_inventory ON garden_placements(user_id,design_id);
ALTER TABLE user_gardens ENABLE ROW LEVEL SECURITY;
ALTER TABLE garden_placements ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON user_gardens,garden_placements FROM PUBLIC;
DO $$ DECLARE role_name TEXT; BEGIN
 FOREACH role_name IN ARRAY ARRAY['anon','authenticated','service_role'] LOOP
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname=role_name) THEN
 EXECUTE format('REVOKE ALL ON user_gardens,garden_placements FROM %I',role_name);
 END IF; END LOOP;
END $$;
