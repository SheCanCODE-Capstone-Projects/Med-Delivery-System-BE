-- V7__add_prescription_validation_columns.sql
-- Add validation columns to prescriptions table
-- Add validator pharmacist foreign key

ALTER TABLE prescriptions
    ADD COLUMN IF NOT EXISTS validated_by_pharmacist BOOLEAN,
    ADD COLUMN IF NOT EXISTS validation_status VARCHAR(50),
    ADD COLUMN IF NOT EXISTS validator_pharmacist_id BIGINT REFERENCES pharmacist_profiles(id);

-- Create index for foreign key
CREATE INDEX IF NOT EXISTS idx_prescriptions_validator_pharmacist 
    ON prescriptions(validator_pharmacist_id);

COMMENT ON COLUMN prescriptions.validated_by_pharmacist IS 'Whether a pharmacist has validated this prescription';
COMMENT ON COLUMN prescriptions.validation_status IS 'Status of pharmacist validation: VALIDATED, REJECTED, PENDING';
COMMENT ON COLUMN prescriptions.validator_pharmacist_id IS 'Reference to pharmacist who validated this prescription';