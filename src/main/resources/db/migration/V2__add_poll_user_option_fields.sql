ALTER TABLE poll_templates
    ADD COLUMN allow_user_option_add BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE polls
    ADD COLUMN allow_user_option_add BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE poll_options
    ADD COLUMN user_added BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE poll_options
    ADD COLUMN created_by_user_id BIGINT;

ALTER TABLE poll_options
    ADD CONSTRAINT fk_poll_options_created_by_user
        FOREIGN KEY (created_by_user_id) REFERENCES users (id);
