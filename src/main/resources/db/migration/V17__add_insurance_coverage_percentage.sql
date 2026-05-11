-- Add coverage_percentage column to insurance_cards table
ALTER TABLE insurance_cards
ADD COLUMN IF NOT EXISTS coverage_percentage NUMERIC(5, 2) DEFAULT 0.00;
