ALTER TABLE media_assets
    ADD COLUMN cleanup_attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN cleanup_next_attempt_at TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN cleanup_last_failed_at TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN cleanup_failure_code VARCHAR(40),
    ADD COLUMN cleanup_lease_token VARCHAR(64),
    ADD COLUMN cleanup_lease_expires_at TIMESTAMP(6) WITH TIME ZONE;

ALTER TABLE media_assets
    ADD CONSTRAINT ck_media_assets_cleanup_attempt_count
        CHECK (cleanup_attempt_count >= 0),
    ADD CONSTRAINT ck_media_assets_cleanup_retry_pair
        CHECK (
            (cleanup_next_attempt_at IS NULL AND cleanup_last_failed_at IS NULL AND cleanup_failure_code IS NULL)
            OR
            (cleanup_next_attempt_at IS NOT NULL AND cleanup_last_failed_at IS NOT NULL AND cleanup_failure_code IS NOT NULL)
        ),
    ADD CONSTRAINT ck_media_assets_cleanup_lease_pair
        CHECK (
            (cleanup_lease_token IS NULL AND cleanup_lease_expires_at IS NULL)
            OR
            (cleanup_lease_token IS NOT NULL AND cleanup_lease_expires_at IS NOT NULL)
        );

CREATE INDEX idx_media_assets_cleanup_due
    ON media_assets (cleanup_next_attempt_at, cleanup_lease_expires_at, id);
