-- Keep the durable idempotency key/fingerprint tombstone forever; only
-- archive old, bulky response payloads. Deleting the unique row would make
-- an old network replay execute the CARE/EVOLVE mutation a second time.
ALTER TABLE companion_instance_operations
  ALTER COLUMN response_json DROP NOT NULL;
ALTER TABLE companion_instance_operations
  ADD COLUMN archived_at TIMESTAMPTZ;
CREATE INDEX ix_companion_instance_ops_archive_pending
  ON companion_instance_operations (created_at)
  WHERE response_json IS NOT NULL;
