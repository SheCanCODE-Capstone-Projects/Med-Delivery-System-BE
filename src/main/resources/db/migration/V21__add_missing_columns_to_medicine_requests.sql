-- Add all missing columns to medicine_requests table to match MedicineRequest entity
ALTER TABLE medicine_requests 
    ADD COLUMN IF NOT EXISTS notes TEXT,
    ADD COLUMN IF NOT EXISTS order_type VARCHAR(30),
    ADD COLUMN IF NOT EXISTS fulfillment_type VARCHAR(20),
    ADD COLUMN IF NOT EXISTS status VARCHAR(30) DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS prescription_id BIGINT,
    ADD COLUMN IF NOT EXISTS insurance_card_id BIGINT,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITHOUT TIME ZONE;

-- Add foreign key constraints
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'medicine_requests' AND constraint_name = 'fk_medicine_request_prescription'
    ) THEN
        ALTER TABLE medicine_requests ADD CONSTRAINT fk_medicine_request_prescription
            FOREIGN KEY (prescription_id) REFERENCES prescriptions(id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'medicine_requests' AND constraint_name = 'fk_medicine_request_insurance_card'
    ) THEN
        ALTER TABLE medicine_requests ADD CONSTRAINT fk_medicine_request_insurance_card
            FOREIGN KEY (insurance_card_id) REFERENCES insurance_cards(id);
    END IF;
END $$;

-- Add check constraints for enum values
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'medicine_requests' AND constraint_name = 'chk_medicine_request_order_type'
    ) THEN
        ALTER TABLE medicine_requests ADD CONSTRAINT chk_medicine_request_order_type
            CHECK (order_type IN ('PRIVATE_PURCHASE', 'PRESCRIPTION_BASED'));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'medicine_requests' AND constraint_name = 'chk_medicine_request_fulfillment_type'
    ) THEN
        ALTER TABLE medicine_requests ADD CONSTRAINT chk_medicine_request_fulfillment_type
            CHECK (fulfillment_type IN ('PICKUP', 'DELIVERY') OR fulfillment_type IS NULL);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'medicine_requests' AND constraint_name = 'chk_medicine_request_status'
    ) THEN
        ALTER TABLE medicine_requests ADD CONSTRAINT chk_medicine_request_status
            CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'FULFILLED', 'CANCELLED'));
    END IF;
END $$;