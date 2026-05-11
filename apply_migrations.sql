-- Apply missing migrations V7-V17
-- V7: Add prescription validation columns
ALTER TABLE prescriptions
    ADD COLUMN IF NOT EXISTS validated_by_pharmacist BOOLEAN,
    ADD COLUMN IF NOT EXISTS validation_status VARCHAR(50),
    ADD COLUMN IF NOT EXISTS validator_pharmacist_id BIGINT REFERENCES pharmacist_profiles(id);
CREATE INDEX IF NOT EXISTS idx_prescriptions_validator_pharmacist ON prescriptions(validator_pharmacist_id);

-- V10: Add expiry_date to prescriptions
ALTER TABLE prescriptions ADD COLUMN IF NOT EXISTS expiry_date DATE;

-- V11: Add created_at to pharmacist_profiles
ALTER TABLE pharmacist_profiles ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW();
UPDATE pharmacist_profiles SET created_at = NOW() WHERE created_at IS NULL;
ALTER TABLE pharmacist_profiles ALTER COLUMN created_at SET NOT NULL;

-- V13: Add payment fields to orders, coverage_percentage to insurance_cards, create payments table
ALTER TABLE orders ADD COLUMN IF NOT EXISTS total_amount DECIMAL(12,2);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS patient_payable_amount DECIMAL(12,2);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS insurance_payable_amount DECIMAL(12,2);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_status VARCHAR(50);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_method VARCHAR(50);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS transaction_id VARCHAR(255);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivery_address TEXT;
ALTER TABLE insurance_cards ADD COLUMN IF NOT EXISTS coverage_percentage DOUBLE PRECISION;
CREATE TABLE IF NOT EXISTS payments (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE REFERENCES orders(id) ON DELETE CASCADE,
    total_amount DECIMAL(12,2) NOT NULL,
    insurance_amount DECIMAL(12,2) NOT NULL,
    patient_amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    payment_method VARCHAR(50) NOT NULL,
    transaction_id VARCHAR(255),
    insurance_provider VARCHAR(255),
    failure_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_payments_order_id ON payments(order_id);
CREATE INDEX IF NOT EXISTS idx_orders_payment_status ON orders(payment_status);
CREATE INDEX IF NOT EXISTS idx_insurance_cards_coverage ON insurance_cards(coverage_percentage);

-- V14: Add STOCK_CONFIRMED to order status enum
ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_order_status;
ALTER TABLE orders ADD CONSTRAINT chk_order_status CHECK (
    status IN ('UPLOADED','MATCHING','ASSIGNED','IN_PROGRESS','STOCK_CONFIRMED','READY_FOR_PICKUP','OUT_FOR_DELIVERY','COMPLETED','CANCELLED')
);

-- V15: Add user profile fields (safe IF NOT EXISTS)
ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_image_url VARCHAR(500);
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_notifications BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS sms_notifications BOOLEAN NOT NULL DEFAULT TRUE;

-- V16: Add verification notes to insurance_cards
ALTER TABLE insurance_cards ADD COLUMN IF NOT EXISTS verified_at TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE insurance_cards ADD COLUMN IF NOT EXISTS verification_notes TEXT;

-- V17: Add coverage_percentage with numeric type
ALTER TABLE insurance_cards ADD COLUMN IF NOT EXISTS coverage_percentage NUMERIC(5,2) DEFAULT 0.00;

-- V19: Add insurance_card_id to medicine_requests
ALTER TABLE medicine_requests ADD COLUMN IF NOT EXISTS insurance_card_id BIGINT;
ALTER TABLE medicine_requests ADD CONSTRAINT IF NOT EXISTS fk_medicine_request_insurance_card
    FOREIGN KEY (insurance_card_id) REFERENCES insurance_cards(id);

-- V20: Add missing notes column to medicine_requests
ALTER TABLE medicine_requests ADD COLUMN IF NOT EXISTS notes TEXT;