ALTER TABLE calendar_daily_rewards ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON calendar_daily_rewards FROM PUBLIC;
REVOKE ALL ON SEQUENCE calendar_daily_rewards_id_seq FROM PUBLIC;
DO $$ DECLARE role_name TEXT; BEGIN
 FOREACH role_name IN ARRAY ARRAY['anon','authenticated','service_role'] LOOP
  IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname=role_name) THEN
   EXECUTE format('REVOKE ALL ON calendar_daily_rewards FROM %I',role_name);
   EXECUTE format('REVOKE ALL ON SEQUENCE calendar_daily_rewards_id_seq FROM %I',role_name);
  END IF;
 END LOOP;
END $$;
