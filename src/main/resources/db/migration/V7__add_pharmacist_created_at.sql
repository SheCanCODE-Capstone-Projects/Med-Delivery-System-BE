ALTER TABLE pharmacist_profiles
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW();

-- Backfill existing rows
UPDATE pharmacist_profiles
SET created_at = NOW()
WHERE created_at IS NULL;

-- Enforce non-null going forward
ALTER TABLE pharmacist_profiles
    ALTER COLUMN created_at SET NOT NULL;
