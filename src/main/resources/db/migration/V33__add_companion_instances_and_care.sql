-- Separate owned instances from the legacy (user_id,design_id,quantity) inventory.
-- Keep the legacy inventory and garden design_id shape compatible with old clients.
CREATE TABLE companion_instances (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id BIGINT NOT NULL REFERENCES users(id),
  design_id VARCHAR(100) NOT NULL REFERENCES companion_designs(design_id),
  level SMALLINT NOT NULL DEFAULT 1 CHECK (level BETWEEN 1 AND 3),
  growth_xp BIGINT NOT NULL DEFAULT 0 CHECK (growth_xp >= 0),
  total_earned_xp BIGINT NOT NULL DEFAULT 0 CHECK (total_earned_xp >= growth_xp),
  care_days INTEGER NOT NULL DEFAULT 0 CHECK (care_days >= 0),
  satiety SMALLINT NOT NULL DEFAULT 100 CHECK (satiety BETWEEN 0 AND 100),
  cleanliness SMALLINT NOT NULL DEFAULT 100 CHECK (cleanliness BETWEEN 0 AND 100),
  joy SMALLINT NOT NULL DEFAULT 100 CHECK (joy BETWEEN 0 AND 100),
  affection SMALLINT NOT NULL DEFAULT 100 CHECK (affection BETWEEN 0 AND 100),
  health SMALLINT NOT NULL DEFAULT 100 CHECK (health BETWEEN 0 AND 100),
  last_evaluated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_cared_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  elapsed_hours BIGINT NOT NULL DEFAULT 0 CHECK (elapsed_hours >= 0),
  last_care_date DATE,
  daily_xp_date DATE,
  daily_xp_earned INTEGER NOT NULL DEFAULT 0 CHECK (daily_xp_earned BETWEEN 0 AND 100),
  acquired_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
  UNIQUE (user_id, id),
  CHECK ((level <> 2 OR growth_xp >= 300) AND (level <> 3 OR growth_xp >= 1000))
);
CREATE INDEX ix_companion_instances_user_design
  ON companion_instances (user_id,design_id,acquired_at,id);
CREATE INDEX ix_companion_instances_owner_level
  ON companion_instances (user_id,level);

CREATE TABLE companion_instance_operations (
  user_id BIGINT NOT NULL,
  instance_id UUID NOT NULL,
  request_key VARCHAR(80) NOT NULL,
  kind VARCHAR(8) NOT NULL CHECK (kind IN ('CARE','EVOLVE')),
  request_fingerprint VARCHAR(160) NOT NULL,
  response_json TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY(user_id,instance_id,request_key),
  FOREIGN KEY (user_id,instance_id) REFERENCES companion_instances(user_id,id)
    ON DELETE CASCADE
);
CREATE INDEX ix_companion_instance_ops_time
  ON companion_instance_operations (user_id,created_at DESC);

CREATE TABLE companion_care_cooldowns (
  instance_id UUID NOT NULL REFERENCES companion_instances(id) ON DELETE CASCADE,
  action VARCHAR(12) NOT NULL CHECK (action IN ('FEED','PET','PLAY','CLEAN','SLEEP')),
  last_used_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY(instance_id,action)
);

-- Users who already drew animals keep precisely the same number of animals.
-- The decline clock starts on migration, not on original acquisition day:
-- existing users cannot be punished for a feature they could not access.
INSERT INTO companion_instances(user_id,design_id,acquired_at)
SELECT uc.user_id,uc.design_id,uc.acquired_at
FROM user_companions uc
CROSS JOIN LATERAL generate_series(1::BIGINT,uc.quantity) AS instance_number;

-- Dual-write via an inventory trigger also covers legacy server code and
-- maintenance SQL that changes quantity, avoiding drift between the two models.
CREATE FUNCTION sync_companion_instance_quantity()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
  difference BIGINT;
  removed BIGINT;
BEGIN
  IF TG_OP='INSERT' THEN
    difference:=NEW.quantity;
  ELSE
    difference:=NEW.quantity-OLD.quantity;
  END IF;

  IF difference>0 THEN
    INSERT INTO companion_instances(user_id,design_id,acquired_at)
    SELECT NEW.user_id,NEW.design_id,now()
    FROM generate_series(1::BIGINT,difference);
  ELSIF difference<0 THEN
    -- Never silently sell a grown, cared-for, or uniquely deployed animal.
    -- Current garden placement is keyed by design: reserve the oldest instance
    -- for that design while a slot uses it, until instance-based garden arrives.
    WITH eligible AS (
      SELECT ci.id
      FROM companion_instances ci
      WHERE ci.user_id=NEW.user_id AND ci.design_id=NEW.design_id
        AND ci.level=1 AND ci.growth_xp=0 AND ci.care_days=0
        AND (
          NOT EXISTS (
            SELECT 1 FROM garden_placements gp
            WHERE gp.user_id=NEW.user_id AND gp.design_id=NEW.design_id
          )
          OR ci.id <> (
            SELECT reserved.id FROM companion_instances reserved
            WHERE reserved.user_id=NEW.user_id AND reserved.design_id=NEW.design_id
            ORDER BY reserved.acquired_at,reserved.id LIMIT 1
          )
        )
      ORDER BY ci.acquired_at DESC,ci.id DESC
      LIMIT -difference FOR UPDATE
    )
    DELETE FROM companion_instances ci USING eligible
    WHERE ci.id=eligible.id;

    GET DIAGNOSTICS removed=ROW_COUNT;
    IF removed <> -difference THEN
      RAISE EXCEPTION 'Not enough untouched companion instances to sell safely';
    END IF;
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER trg_sync_companion_instances
AFTER INSERT OR UPDATE OF quantity ON user_companions
FOR EACH ROW EXECUTE FUNCTION sync_companion_instance_quantity();

ALTER TABLE companion_instances ENABLE ROW LEVEL SECURITY;
ALTER TABLE companion_instance_operations ENABLE ROW LEVEL SECURITY;
ALTER TABLE companion_care_cooldowns ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON companion_instances,companion_instance_operations,companion_care_cooldowns FROM PUBLIC;
DO $$ DECLARE role_name TEXT; BEGIN
 FOREACH role_name IN ARRAY ARRAY['anon','authenticated','service_role'] LOOP
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname=role_name) THEN
   EXECUTE format(
    'REVOKE ALL ON companion_instances,companion_instance_operations,companion_care_cooldowns FROM %I',
    role_name
   );
  END IF;
 END LOOP;
END $$;
