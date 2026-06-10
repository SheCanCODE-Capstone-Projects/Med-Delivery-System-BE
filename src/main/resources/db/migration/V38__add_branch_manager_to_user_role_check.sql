-- The original chk_user_role constraint was created before BRANCH_MANAGER was added as a role.
-- Drop and recreate it to include BRANCH_MANAGER.
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_user_role;

ALTER TABLE users ADD CONSTRAINT chk_user_role
    CHECK (role IN ('PATIENT', 'PHARMACIST', 'MANAGER', 'SUPER_ADMIN', 'BRANCH_MANAGER'));
