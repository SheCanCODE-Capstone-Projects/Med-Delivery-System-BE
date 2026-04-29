-- V3__add_license_number_to_pharmacies.sql

-- Add license_number column
ALTER TABLE pharmacies ADD COLUMN IF NOT EXISTS license_number VARCHAR(255);

-- Backfill existing rows with a placeholder using pharmacy_code
UPDATE pharmacies SET license_number = 'LICENSE-' || pharmacy_code WHERE license_number IS NULL;

-- Enforce NOT NULL constraint
ALTER TABLE pharmacies ALTER COLUMN license_number SET NOT NULL;

-- Add comment
COMMENT ON COLUMN pharmacies.license_number IS 'Official pharmacy license number issued by regulatory authority';
