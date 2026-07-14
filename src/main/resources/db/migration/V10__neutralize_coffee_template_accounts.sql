-- COFFEE templates are shared by all active COFFEE duties.
-- The actual poll creator supplies an owned active COFFEE account when creating a poll.
UPDATE poll_templates
SET payment_account_id = NULL
WHERE poll_type = 'COFFEE'
  AND payment_account_id IS NOT NULL;
