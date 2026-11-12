CREATE TABLE mission_claims (
 user_id BIGINT NOT NULL REFERENCES users(id),
 mission_code VARCHAR(32) NOT NULL CHECK(mission_code IN ('WEEKLY_SUBMISSION','WEEKLY_VOTE','WEEKLY_NO_PENALTY','MONTHLY_NO_PENALTY','MONTHLY_ALL_SUBMISSIONS')),
 period_start DATE NOT NULL,
 source_campus_id BIGINT REFERENCES campuses(id) ON DELETE SET NULL,
 points BIGINT NOT NULL CHECK(points>0),
 business_key VARCHAR(160) NOT NULL,
 evidence_json JSONB NOT NULL,
 claimed_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY(user_id,mission_code,period_start),
 UNIQUE(user_id,business_key),
 FOREIGN KEY(user_id,business_key) REFERENCES point_transactions(user_id,business_key)
);
CREATE INDEX ix_mission_claims_source_campus ON mission_claims(source_campus_id);
ALTER TABLE mission_claims ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON mission_claims FROM PUBLIC;
DO $$ DECLARE role_name TEXT; BEGIN
 FOREACH role_name IN ARRAY ARRAY['anon','authenticated','service_role'] LOOP
  IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname=role_name) THEN
   EXECUTE format('REVOKE ALL ON mission_claims FROM %I',role_name);
  END IF;
 END LOOP;
END $$;
