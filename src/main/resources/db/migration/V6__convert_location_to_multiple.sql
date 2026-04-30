-- V6__convert_location_to_multiple.sql
-- Changes patient_locations to support multiple addresses per patient

-- Step 1: Drop unique constraint on patient_profile_id (allows many-to-one)
ALTER TABLE patient_locations DROP CONSTRAINT IF EXISTS patient_locations_patient_profile_id_key;

-- Step 2: Add is_default column to mark primary location
ALTER TABLE patient_locations
    ADD COLUMN IF NOT EXISTS is_default BOOLEAN NOT NULL DEFAULT FALSE;

-- Comment
COMMENT ON COLUMN patient_locations.is_default IS 'Flag indicating if this location is the default for the patient';

-- Step 3: Create index for fast default lookup
CREATE INDEX IF NOT EXISTS idx_patient_locations_default
    ON patient_locations (patient_profile_id, is_default)
    WHERE is_default = TRUE;
