-- V4__add_extended_fields_to_patient_profiles.sql
-- Adds additional optional fields to patient_profiles for demographics and medical info

ALTER TABLE patient_profiles
    ADD COLUMN IF NOT EXISTS date_of_birth DATE,
    ADD COLUMN IF NOT EXISTS gender VARCHAR(20),
    ADD COLUMN IF NOT EXISTS allergies TEXT,
    ADD COLUMN IF NOT EXISTS medical_notes TEXT;

-- Comments
COMMENT ON COLUMN patient_profiles.date_of_birth IS 'Patient date of birth';
COMMENT ON COLUMN patient_profiles.gender IS 'Patient gender';
COMMENT ON COLUMN patient_profiles.allergies IS 'Known allergies';
COMMENT ON COLUMN patient_profiles.medical_notes IS 'Additional medical information or notes';
