ALTER TABLE charge_items
    ADD CONSTRAINT ck_charge_items_amount_positive
    CHECK (amount > 0) NOT VALID;

ALTER TABLE charge_items
    VALIDATE CONSTRAINT ck_charge_items_amount_positive;
