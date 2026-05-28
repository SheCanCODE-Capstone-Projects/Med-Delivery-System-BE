ALTER TABLE patient_profiles
    ADD COLUMN IF NOT EXISTS blood_type VARCHAR(10);
