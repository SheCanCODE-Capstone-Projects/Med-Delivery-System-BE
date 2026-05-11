-- Add verification_notes and verified_at columns to insurance_cards table
ALTER TABLE insurance_cards
ADD COLUMN IF NOT EXISTS verified_at TIMESTAMP WITHOUT TIME ZONE,
ADD COLUMN IF NOT EXISTS verification_notes TEXT;
