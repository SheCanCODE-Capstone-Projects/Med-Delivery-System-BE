-- V8__add_payment_tables_and_insurance_coverage.sql
-- Add payment fields to orders table
-- Create payments table
-- Add coveragePercentage to insurance_cards

-- 1. Add payment columns to orders table
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS total_amount DECIMAL(12,2),
    ADD COLUMN IF NOT EXISTS patient_payable_amount DECIMAL(12,2),
    ADD COLUMN IF NOT EXISTS insurance_payable_amount DECIMAL(12,2),
    ADD COLUMN IF NOT EXISTS payment_status VARCHAR(50),
    ADD COLUMN IF NOT EXISTS payment_method VARCHAR(50),
    ADD COLUMN IF NOT EXISTS transaction_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS delivery_address TEXT;

-- 2. Add coveragePercentage to insurance_cards
ALTER TABLE insurance_cards
    ADD COLUMN IF NOT EXISTS coverage_percentage DOUBLE PRECISION;

-- 3. Create payments table
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

-- 4. Create indexes
CREATE INDEX IF NOT EXISTS idx_payments_order_id ON payments(order_id);
CREATE INDEX IF NOT EXISTS idx_orders_payment_status ON orders(payment_status);
CREATE INDEX IF NOT EXISTS idx_insurance_cards_coverage ON insurance_cards(coverage_percentage);

-- 5. Add comments
COMMENT ON COLUMN orders.total_amount IS 'Total order amount before insurance';
COMMENT ON COLUMN orders.patient_payable_amount IS 'Amount patient needs to pay after insurance coverage';
COMMENT ON COLUMN orders.insurance_payable_amount IS 'Amount covered by insurance';
COMMENT ON COLUMN orders.payment_status IS 'Payment status: PENDING, PAID, INSURANCE_PENDING, FAILED, etc.';
COMMENT ON COLUMN orders.payment_method IS 'Payment method: CASH_ON_DELIVERY, INSURANCE, CREDIT_CARD, etc.';
COMMENT ON COLUMN orders.transaction_id IS 'Payment gateway transaction reference';
COMMENT ON COLUMN orders.delivery_address IS 'Delivery address for orders with fulfillment type DELIVERY';
COMMENT ON COLUMN insurance_cards.coverage_percentage IS 'Percentage of costs covered by this insurance (e.g., 80.0 for 80%)';
COMMENT ON COLUMN payments.total_amount IS 'Total order amount';
COMMENT ON COLUMN payments.insurance_amount IS 'Portion covered by insurance';
COMMENT ON COLUMN payments.patient_amount IS 'Portion patient is responsible for';
COMMENT ON COLUMN payments.status IS 'Payment status';
COMMENT ON COLUMN payments.payment_method IS 'Method of payment';
COMMENT ON COLUMN payments.insurance_provider IS 'Name of insurance provider (if applicable)';
