WITH duplicated_active_client_tokens AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY user_id, client_instance_id
            ORDER BY last_refreshed_at DESC NULLS LAST, updated_at DESC, id DESC
        ) AS row_number
    FROM user_fcm_tokens
    WHERE is_active = TRUE
)
UPDATE user_fcm_tokens token
SET
    is_active = FALSE,
    deactivated_at = COALESCE(token.deactivated_at, CURRENT_TIMESTAMP),
    updated_at = CURRENT_TIMESTAMP
FROM duplicated_active_client_tokens duplicated
WHERE token.id = duplicated.id
  AND duplicated.row_number > 1;

ALTER TABLE user_fcm_tokens
    DROP CONSTRAINT IF EXISTS uk_user_fcm_tokens_token;

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_fcm_tokens_active_token
    ON user_fcm_tokens (token)
    WHERE is_active = TRUE;

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_fcm_tokens_active_user_client
    ON user_fcm_tokens (user_id, client_instance_id)
    WHERE is_active = TRUE;
