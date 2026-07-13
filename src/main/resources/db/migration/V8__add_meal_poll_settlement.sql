ALTER TABLE campus_duty_assignments
    DROP CONSTRAINT ck_campus_duty_assignments_type,
    ADD CONSTRAINT ck_campus_duty_assignments_type
        CHECK (duty_type IN ('COFFEE', 'MEAL')) NOT VALID;

ALTER TABLE campus_duty_assignments
    VALIDATE CONSTRAINT ck_campus_duty_assignments_type;

DROP INDEX uk_campus_duty_assignments_active_duty;

CREATE UNIQUE INDEX uk_campus_duty_assignments_active_coffee
    ON campus_duty_assignments (campus_id, duty_type)
    WHERE is_active = TRUE AND duty_type = 'COFFEE';

CREATE UNIQUE INDEX uk_campus_duty_assignments_active_meal_user
    ON campus_duty_assignments (campus_id, duty_type, user_id)
    WHERE is_active = TRUE AND duty_type = 'MEAL';

ALTER TABLE payment_accounts
    DROP CONSTRAINT ck_payment_accounts_account_type,
    ADD CONSTRAINT ck_payment_accounts_account_type
        CHECK (account_type IN ('PENALTY', 'COFFEE', 'MEAL')) NOT VALID;

ALTER TABLE payment_accounts
    VALIDATE CONSTRAINT ck_payment_accounts_account_type;

CREATE UNIQUE INDEX uk_payment_accounts_active_meal_owner
    ON payment_accounts (campus_id, account_type, owner_user_id)
    WHERE is_active = TRUE AND account_type = 'MEAL';

ALTER TABLE polls
    DROP CONSTRAINT ck_polls_poll_type,
    ADD CONSTRAINT ck_polls_poll_type
        CHECK (poll_type IN ('WED_SERVICE', 'SATURDAY_LEADER', 'COFFEE', 'MEAL', 'CUSTOM')) NOT VALID;

ALTER TABLE polls
    VALIDATE CONSTRAINT ck_polls_poll_type;

ALTER TABLE charge_items
    DROP CONSTRAINT ck_charge_items_payment_category,
    ADD CONSTRAINT ck_charge_items_payment_category
        CHECK (payment_category IN ('PENALTY', 'COFFEE', 'MEAL')) NOT VALID;

ALTER TABLE charge_items
    VALIDATE CONSTRAINT ck_charge_items_payment_category;

CREATE TABLE meal_poll_settlements (
    id BIGSERIAL PRIMARY KEY,
    campus_id BIGINT NOT NULL,
    poll_id BIGINT NOT NULL,
    payment_account_id BIGINT NOT NULL,
    charged_by_user_id BIGINT NOT NULL,
    requested_total_amount BIGINT NOT NULL,
    actual_total_amount BIGINT NOT NULL,
    rounding_adjustment BIGINT NOT NULL,
    charged_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_meal_poll_settlements_poll UNIQUE (poll_id),
    CONSTRAINT ck_meal_poll_settlements_amounts CHECK (
        requested_total_amount >= 0
        AND actual_total_amount >= 0
        AND rounding_adjustment >= 0
        AND actual_total_amount = requested_total_amount + rounding_adjustment
    ),
    CONSTRAINT fk_meal_poll_settlements_campus FOREIGN KEY (campus_id) REFERENCES campuses (id),
    CONSTRAINT fk_meal_poll_settlements_poll FOREIGN KEY (poll_id) REFERENCES polls (id) ON DELETE CASCADE,
    CONSTRAINT fk_meal_poll_settlements_payment_account FOREIGN KEY (payment_account_id) REFERENCES payment_accounts (id),
    CONSTRAINT fk_meal_poll_settlements_charged_by FOREIGN KEY (charged_by_user_id) REFERENCES users (id)
);

CREATE TABLE meal_poll_charge_groups (
    id BIGSERIAL PRIMARY KEY,
    settlement_id BIGINT NOT NULL,
    poll_id BIGINT NOT NULL,
    option_id BIGINT NOT NULL,
    calculation_type VARCHAR(30) NOT NULL,
    entered_amount BIGINT NOT NULL,
    response_count_snapshot INTEGER NOT NULL,
    amount_per_member INTEGER NOT NULL,
    requested_total_amount BIGINT NOT NULL,
    actual_total_amount BIGINT NOT NULL,
    rounding_adjustment BIGINT NOT NULL,
    CONSTRAINT uk_meal_poll_charge_groups_poll_option UNIQUE (poll_id, option_id),
    CONSTRAINT ck_meal_poll_charge_groups_calculation_type
        CHECK (calculation_type IN ('PER_MEMBER', 'GROUP_TOTAL')),
    CONSTRAINT ck_meal_poll_charge_groups_amounts CHECK (
        entered_amount > 0
        AND response_count_snapshot > 0
        AND amount_per_member > 0
        AND requested_total_amount > 0
        AND actual_total_amount > 0
        AND rounding_adjustment >= 0
        AND actual_total_amount = requested_total_amount + rounding_adjustment
    ),
    CONSTRAINT fk_meal_poll_charge_groups_settlement FOREIGN KEY (settlement_id) REFERENCES meal_poll_settlements (id),
    CONSTRAINT fk_meal_poll_charge_groups_poll FOREIGN KEY (poll_id) REFERENCES polls (id),
    CONSTRAINT fk_meal_poll_charge_groups_option FOREIGN KEY (option_id) REFERENCES poll_options (id) ON DELETE CASCADE
);
