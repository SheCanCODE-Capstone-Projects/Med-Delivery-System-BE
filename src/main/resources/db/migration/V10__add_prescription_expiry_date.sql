-- V10__add_prescription_expiry_date.sql
-- Add expiry date to prescriptions for tracking validity

ALTER TABLE prescriptions
    ADD COLUMN IF NOT EXISTS expiry_date DATE;

COMMENT ON COLUMN prescriptions.expiry_date IS 'Expiry date of the prescription (if applicable)';
