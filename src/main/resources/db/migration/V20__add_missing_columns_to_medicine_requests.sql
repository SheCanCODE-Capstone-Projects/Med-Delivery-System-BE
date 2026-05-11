ALTER TABLE medicine_requests ADD COLUMN IF NOT EXISTS notes TEXT;
ALTER TABLE medicine_requests ADD COLUMN IF NOT EXISTS insurance_card_id BIGINT;

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