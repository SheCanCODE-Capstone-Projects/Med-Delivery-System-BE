-- Quick Fix: Run this SQL directly in PostgreSQL

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS assigned_at TIMESTAMP WITHOUT TIME ZONE;

-- Verify the column was added
SELECT column_name, data_type 
FROM information_schema.columns 
WHERE table_name = 'orders' AND column_name = 'assigned_at';
