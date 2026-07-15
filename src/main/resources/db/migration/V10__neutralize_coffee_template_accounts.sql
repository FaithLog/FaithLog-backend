-- Valid COFFEE templates are shared by all active COFFEE duties.
-- The actual poll creator supplies an owned active COFFEE account when creating a poll.
UPDATE poll_templates
SET payment_account_id = NULL
WHERE poll_type = 'COFFEE'
  AND charge_generation_type = 'OPTION_PRICE'
  AND payment_category = 'COFFEE'
  AND payment_account_id IS NOT NULL;

-- Legacy rows could encode a COFFEE operation with a non-COFFEE poll type or an incomplete
-- COFFEE configuration. Preserve the row for audit, but fail closed by disabling it.
UPDATE poll_templates
SET is_active = FALSE,
    auto_create_enabled = FALSE,
    payment_account_id = NULL
WHERE (
    poll_type = 'COFFEE'
    OR charge_generation_type = 'OPTION_PRICE'
    OR payment_category = 'COFFEE'
  )
  AND NOT (
    poll_type = 'COFFEE'
    AND charge_generation_type = 'OPTION_PRICE'
    AND COALESCE(payment_category, '') = 'COFFEE'
  );
