CREATE TABLE companion_growth_shards (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    balance BIGINT NOT NULL CHECK (balance >= 0),
    last_boost_date DATE
);
CREATE TABLE companion_shard_exchanges (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    request_key VARCHAR(80) NOT NULL,
    design_id VARCHAR(100) NOT NULL REFERENCES companion_designs(design_id),
    awarded BIGINT NOT NULL CHECK (awarded > 0),
    resulting_balance BIGINT NOT NULL CHECK (resulting_balance >= awarded),
    PRIMARY KEY (user_id, request_key)
);
ALTER TABLE companion_growth_shards ENABLE ROW LEVEL SECURITY;
ALTER TABLE companion_shard_exchanges ENABLE ROW LEVEL SECURITY;

ALTER TABLE companion_instance_operations DROP CONSTRAINT companion_instance_operations_kind_check;
ALTER TABLE companion_instance_operations ADD CONSTRAINT companion_instance_operations_kind_check
    CHECK (kind IN ('CARE','EVOLVE','BOOST'));

CREATE OR REPLACE FUNCTION sync_companion_instance_quantity()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
  difference BIGINT;
  removed BIGINT;
BEGIN
  IF TG_OP = 'INSERT' THEN
    difference := NEW.quantity;
  ELSE
    difference := NEW.quantity - OLD.quantity;
  END IF;

  IF difference > 0 THEN
    INSERT INTO companion_instances(user_id,design_id,acquired_at)
    SELECT NEW.user_id, NEW.design_id, now()
    FROM generate_series(1::BIGINT,difference);
  ELSIF difference < 0 THEN
    WITH eligible AS (
      SELECT ci.id
      FROM companion_instances ci
      WHERE ci.user_id=NEW.user_id
        AND ci.design_id=NEW.design_id
        AND ci.level=1
        AND ci.total_earned_xp=0
        AND ci.care_days=0
        AND NOT EXISTS (
          SELECT 1 FROM garden_placements gp
          WHERE gp.user_id=ci.user_id AND gp.instance_id=ci.id
        )
      ORDER BY ci.acquired_at DESC, ci.id DESC
      LIMIT -difference FOR UPDATE
    )
    DELETE FROM companion_instances ci USING eligible
    WHERE ci.id=eligible.id;

    GET DIAGNOSTICS removed=ROW_COUNT;
    IF removed <> -difference THEN
      RAISE EXCEPTION 'Not enough untouched, unequipped companion instances to sell safely';
    END IF;
  END IF;
  RETURN NEW;
END $$;
