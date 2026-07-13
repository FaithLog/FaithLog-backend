ALTER TABLE charge_items
    ADD CONSTRAINT ck_charge_items_amount_positive
    CHECK (amount > 0) NOT VALID;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM charge_items WHERE amount <= 0) THEN
        ALTER TABLE charge_items
            VALIDATE CONSTRAINT ck_charge_items_amount_positive;
    END IF;
END
$$;
