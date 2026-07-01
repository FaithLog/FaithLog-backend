ALTER TABLE payment_accounts
    ADD COLUMN deleted_at TIMESTAMP(6) WITH TIME ZONE;
