-- V31: Add Branch Manager role, Branch entity, invitation tokens, login events

CREATE TABLE IF NOT EXISTS branches (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    address TEXT,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    contact_info VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    pharmacy_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_branch_pharmacy FOREIGN KEY (pharmacy_id) REFERENCES pharmacies(id)
);

CREATE TABLE IF NOT EXISTS branch_manager_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT UNIQUE NOT NULL,
    branch_id BIGINT NOT NULL,
    activated_at TIMESTAMP,
    CONSTRAINT fk_bmp_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_bmp_branch FOREIGN KEY (branch_id) REFERENCES branches(id)
);

DO $$
BEGIN
    ALTER TABLE pharmacist_profiles
        ADD COLUMN IF NOT EXISTS branch_id BIGINT;
    ALTER TABLE pharmacist_profiles
        ADD CONSTRAINT fk_pharmacist_branch FOREIGN KEY (branch_id) REFERENCES branches(id);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    ALTER TABLE pharmacy_inventory
        ADD COLUMN IF NOT EXISTS branch_id BIGINT;
    ALTER TABLE pharmacy_inventory
        ADD CONSTRAINT fk_inventory_branch FOREIGN KEY (branch_id) REFERENCES branches(id);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

CREATE TABLE IF NOT EXISTS invitation_tokens (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(255) UNIQUE NOT NULL,
    email VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    payload TEXT,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS login_events (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    email VARCHAR(255),
    ip_address VARCHAR(100),
    user_agent TEXT,
    success BOOLEAN NOT NULL,
    failure_reason VARCHAR(255),
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_login_event_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);
