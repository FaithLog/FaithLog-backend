ALTER TABLE media_assets
    ADD COLUMN asset_kind VARCHAR(10) NOT NULL DEFAULT 'IMAGE',
    ADD COLUMN original_file_name VARCHAR(255),
    ADD COLUMN document_object_key VARCHAR(200);

ALTER TABLE media_assets
    ALTER COLUMN asset_kind DROP DEFAULT,
    DROP CONSTRAINT ck_media_assets_input_content_type,
    DROP CONSTRAINT ck_media_assets_input_byte_size;

ALTER TABLE media_assets
    ADD CONSTRAINT uk_media_assets_document_object_key UNIQUE (document_object_key),
    ADD CONSTRAINT ck_media_assets_asset_kind CHECK (asset_kind IN ('IMAGE', 'PDF')),
    ADD CONSTRAINT ck_media_assets_input_content_type CHECK (
        (asset_kind = 'IMAGE' AND input_content_type IN ('image/jpeg', 'image/png'))
        OR (asset_kind = 'PDF' AND input_content_type = 'application/pdf')
    ),
    ADD CONSTRAINT ck_media_assets_input_byte_size CHECK (
        (asset_kind = 'IMAGE' AND input_byte_size BETWEEN 1 AND 5242880)
        OR (asset_kind = 'PDF' AND input_byte_size BETWEEN 1 AND 31457280)
    ),
    ADD CONSTRAINT ck_media_assets_kind_metadata CHECK (
        (asset_kind = 'IMAGE'
            AND document_object_key IS NULL)
        OR
        (asset_kind = 'PDF'
            AND original_file_name IS NOT NULL
            AND char_length(btrim(original_file_name)) BETWEEN 1 AND 255
            AND lower(right(original_file_name, 4)) = '.pdf'
            AND position('/' in original_file_name) = 0
            AND position(chr(92) in original_file_name) = 0
            AND position('..' in original_file_name) = 0
            AND original_file_name !~ '[[:cntrl:]]'
            AND thumbnail_object_key IS NULL
            AND detail_object_key IS NULL
            AND width IS NULL
            AND height IS NULL)
    ),
    ADD CONSTRAINT ck_media_assets_ready_metadata CHECK (
        status NOT IN ('READY', 'ORPHANED')
        OR (asset_kind = 'IMAGE'
            AND thumbnail_object_key IS NOT NULL
            AND detail_object_key IS NOT NULL
            AND document_object_key IS NULL
            AND output_sha256 IS NOT NULL
            AND output_byte_size IS NOT NULL
            AND width IS NOT NULL
            AND height IS NOT NULL)
        OR (asset_kind = 'PDF'
            AND document_object_key IS NOT NULL
            AND thumbnail_object_key IS NULL
            AND detail_object_key IS NULL
            AND output_sha256 IS NOT NULL
            AND output_byte_size BETWEEN 1 AND 31457280
            AND width IS NULL
            AND height IS NULL)
    );

CREATE TABLE announcement_documents (
    id BIGSERIAL PRIMARY KEY,
    campus_id BIGINT NOT NULL,
    announcement_id BIGINT NOT NULL,
    media_asset_id BIGINT NOT NULL,
    display_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_announcement_documents_announcement
        FOREIGN KEY (campus_id, announcement_id) REFERENCES announcements (campus_id, id),
    CONSTRAINT fk_announcement_documents_media_asset
        FOREIGN KEY (campus_id, media_asset_id) REFERENCES media_assets (campus_id, id),
    CONSTRAINT uk_announcement_documents_media_asset UNIQUE (media_asset_id),
    CONSTRAINT uk_announcement_documents_order UNIQUE (announcement_id, display_order)
        DEFERRABLE INITIALLY IMMEDIATE,
    CONSTRAINT ck_announcement_documents_display_order CHECK (display_order >= 0)
);

CREATE INDEX idx_announcement_documents_announcement_order
    ON announcement_documents (announcement_id, display_order, id);

CREATE TABLE poll_documents (
    id BIGSERIAL PRIMARY KEY,
    campus_id BIGINT NOT NULL,
    poll_id BIGINT NOT NULL,
    media_asset_id BIGINT NOT NULL,
    display_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_poll_documents_poll
        FOREIGN KEY (campus_id, poll_id) REFERENCES polls (campus_id, id),
    CONSTRAINT fk_poll_documents_media_asset
        FOREIGN KEY (campus_id, media_asset_id) REFERENCES media_assets (campus_id, id),
    CONSTRAINT uk_poll_documents_media_asset UNIQUE (media_asset_id),
    CONSTRAINT uk_poll_documents_order UNIQUE (poll_id, display_order)
        DEFERRABLE INITIALLY IMMEDIATE,
    CONSTRAINT ck_poll_documents_display_order CHECK (display_order >= 0)
);

CREATE INDEX idx_poll_documents_poll_order
    ON poll_documents (poll_id, display_order, id);

ALTER TABLE announcement_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE poll_documents ENABLE ROW LEVEL SECURITY;
