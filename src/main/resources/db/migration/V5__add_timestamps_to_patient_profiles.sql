-- V5__add_timestamps_to_patient_profiles.sql
-- Adds created_at and updated_at columns to patient_profiles

ALTER TABLE patient_profiles
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITHOUT TIME ZONE;

-- Comments
COMMENT ON COLUMN patient_profiles.created_at IS 'Record creation timestamp';
COMMENT ON COLUMN patient_profiles.updated_at IS 'Record last update timestamp';

-- Create indexes for potential sorting queries
CREATE INDEX IF NOT EXISTS idx_patient_profiles_created_at
    ON patient_profiles (created_at);
