CREATE TABLE companion_care_gift_claims (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    instance_id UUID NOT NULL,
    milestone INTEGER NOT NULL CHECK (milestone IN (3, 7, 14)),
    claim_date DATE NOT NULL,
    PRIMARY KEY (user_id, instance_id, milestone),
    UNIQUE (user_id, claim_date),
    FOREIGN KEY (user_id, instance_id) REFERENCES companion_instances(user_id, id) ON DELETE CASCADE
);

ALTER TABLE companion_care_gift_claims ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON companion_care_gift_claims FROM PUBLIC;
DO $$
DECLARE role_name TEXT;
BEGIN
    FOREACH role_name IN ARRAY ARRAY['anon', 'authenticated', 'service_role'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = role_name) THEN
            EXECUTE format('REVOKE ALL ON companion_care_gift_claims FROM %I', role_name);
        END IF;
    END LOOP;
END $$;
