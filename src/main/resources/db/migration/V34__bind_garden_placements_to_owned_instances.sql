-- V34: Keep the original garden user/slot and design_id contract while
-- connecting every slot to an exact owned animal instance.
ALTER TABLE companion_instances
  ADD CONSTRAINT uq_companion_instances_user_id_design
  UNIQUE (user_id,id,design_id);

ALTER TABLE garden_placements ADD COLUMN instance_id UUID;

-- Existing placements are design-scoped; deterministically assign the
-- oldest matching instance. Existing positions/backgrounds remain untouched.
UPDATE garden_placements gp
SET instance_id=(
  SELECT ci.id
  FROM companion_instances ci
  WHERE ci.user_id=gp.user_id AND ci.design_id=gp.design_id
  ORDER BY ci.acquired_at,ci.id
  LIMIT 1
);
DO $$
BEGIN
 IF EXISTS (SELECT 1 FROM garden_placements WHERE instance_id IS NULL) THEN
  RAISE EXCEPTION 'Garden instance backfill failed; refusing lossy migration';
 END IF;
END $$;

ALTER TABLE garden_placements
  ALTER COLUMN instance_id SET NOT NULL;
ALTER TABLE garden_placements
  ADD CONSTRAINT fk_garden_placement_owned_instance
  FOREIGN KEY (user_id,instance_id,design_id)
  REFERENCES companion_instances(user_id,id,design_id);
ALTER TABLE garden_placements
  DROP CONSTRAINT garden_placements_user_id_design_id_key;
ALTER TABLE garden_placements
  ADD CONSTRAINT uq_garden_placements_user_instance
  UNIQUE(user_id,instance_id);

-- Legacy inventory sales must not delete even a non-oldest equipped instance.
-- Instance write and legacy quantity update remain atomic under the account lock.
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
        AND ci.growth_xp=0
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
