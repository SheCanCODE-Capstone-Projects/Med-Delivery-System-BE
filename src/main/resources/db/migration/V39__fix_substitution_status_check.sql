-- chk_substitution_status (from V1__init) predates the SubstitutionStatus.PENDING value
-- the application actually uses, so every substitution insert was rejected.
-- Recreate the constraint to allow all current SubstitutionStatus enum values.
ALTER TABLE substitution_requests DROP CONSTRAINT IF EXISTS chk_substitution_status;

ALTER TABLE substitution_requests ADD CONSTRAINT chk_substitution_status
    CHECK (status IN ('PENDING', 'PENDING_PATIENT_APPROVAL', 'APPROVED', 'REJECTED'));
