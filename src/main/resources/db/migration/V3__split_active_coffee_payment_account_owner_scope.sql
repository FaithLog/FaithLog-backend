DROP INDEX IF EXISTS uk_payment_accounts_active_type;

CREATE UNIQUE INDEX uk_payment_accounts_active_penalty_type
    ON payment_accounts (campus_id, account_type)
    WHERE is_active = TRUE AND account_type = 'PENALTY';

CREATE UNIQUE INDEX uk_payment_accounts_active_coffee_owner
    ON payment_accounts (campus_id, account_type, owner_user_id)
    WHERE is_active = TRUE AND account_type = 'COFFEE';
