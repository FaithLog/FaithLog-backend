-- Every existing account receives +1000 once; preserve all earned/spent points.
-- Lock account creation and settlement while installing the backfill and signup trigger.
LOCK TABLE users IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE point_wallets, point_transactions IN SHARE ROW EXCLUSIVE MODE;

INSERT INTO point_wallets(user_id,balance,version)
SELECT id,0,0 FROM users
ON CONFLICT(user_id) DO NOTHING;

WITH credited AS (
 INSERT INTO point_transactions(user_id,delta,balance_after,business_key,created_at)
 SELECT w.user_id,1000,w.balance+1000,'welcome:initial-1000:v1',CURRENT_TIMESTAMP
 FROM point_wallets w
 WHERE NOT EXISTS (
  SELECT 1 FROM point_transactions t
  WHERE t.user_id=w.user_id AND t.business_key='welcome:initial-1000:v1'
 )
 ON CONFLICT(user_id,business_key) DO NOTHING
 RETURNING user_id
)
UPDATE point_wallets w SET balance=w.balance+1000,version=w.version+1
FROM credited c WHERE c.user_id=w.user_id;

-- New accounts receive the same gift atomically with signup, through every creation path.
CREATE FUNCTION grant_initial_points_on_signup() RETURNS trigger
LANGUAGE plpgsql SET search_path = public, pg_temp AS $$
BEGIN
 INSERT INTO point_wallets(user_id,balance,version) VALUES(NEW.id,1000,0);
 INSERT INTO point_transactions(user_id,delta,balance_after,business_key,created_at)
 VALUES(NEW.id,1000,1000,'welcome:initial-1000:v1',CURRENT_TIMESTAMP);
 RETURN NEW;
END;
$$;

REVOKE ALL ON FUNCTION grant_initial_points_on_signup() FROM PUBLIC;
DO $$ DECLARE role_name TEXT; BEGIN
 FOREACH role_name IN ARRAY ARRAY['anon','authenticated','service_role'] LOOP
  IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname=role_name) THEN
   EXECUTE format('REVOKE ALL ON FUNCTION grant_initial_points_on_signup() FROM %I',role_name);
  END IF;
 END LOOP;
END $$;

CREATE TRIGGER users_grant_initial_points
AFTER INSERT ON users FOR EACH ROW EXECUTE FUNCTION grant_initial_points_on_signup();
