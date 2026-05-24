ALTER TABLE pharmacy_inventory
    ADD COLUMN IF NOT EXISTS unit                VARCHAR(50),
    ADD COLUMN IF NOT EXISTS expiry_date         DATE,
    ADD COLUMN IF NOT EXISTS low_stock_threshold INTEGER;
