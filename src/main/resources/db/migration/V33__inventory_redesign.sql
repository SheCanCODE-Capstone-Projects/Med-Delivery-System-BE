-- Enhance medicines table with master data fields
ALTER TABLE medicines
    ADD COLUMN IF NOT EXISTS category        VARCHAR(100),
    ADD COLUMN IF NOT EXISTS unit            VARCHAR(50),
    ADD COLUMN IF NOT EXISTS selling_price   NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS low_stock_alert INTEGER DEFAULT 20,
    ADD COLUMN IF NOT EXISTS description     TEXT,
    ADD COLUMN IF NOT EXISTS created_at      TIMESTAMP DEFAULT NOW();

-- Create stock_entries table for batch tracking
CREATE TABLE IF NOT EXISTS stock_entries (
    id                  BIGSERIAL PRIMARY KEY,
    medicine_id         BIGINT NOT NULL,
    branch_id           BIGINT NOT NULL,
    batch_number        VARCHAR(100),
    quantity_received   INTEGER NOT NULL CHECK (quantity_received > 0),
    purchase_price      NUMERIC(12,2),
    supplier            VARCHAR(200),
    manufacturing_date  DATE,
    expiry_date         DATE,
    notes               TEXT,
    created_at          TIMESTAMP DEFAULT NOW(),
    CONSTRAINT fk_stock_entry_medicine FOREIGN KEY (medicine_id) REFERENCES medicines(id) ON DELETE CASCADE,
    CONSTRAINT fk_stock_entry_branch   FOREIGN KEY (branch_id)   REFERENCES branches(id)  ON DELETE CASCADE
);

-- Migrate existing branch inventory into stock_entries (one entry per existing record)
INSERT INTO stock_entries
    (medicine_id, branch_id, batch_number, quantity_received, purchase_price, expiry_date, created_at)
SELECT
    pi.medicine_id,
    pi.branch_id,
    'INITIAL-BATCH',
    GREATEST(pi.quantity, 1),
    pi.price,
    pi.expiry_date,
    COALESCE(pi.last_updated, NOW())
FROM pharmacy_inventory pi
WHERE pi.branch_id IS NOT NULL AND pi.quantity > 0;
