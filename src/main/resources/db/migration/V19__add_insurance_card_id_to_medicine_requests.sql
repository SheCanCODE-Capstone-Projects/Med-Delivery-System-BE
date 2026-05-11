ALTER TABLE medicine_requests ADD COLUMN insurance_card_id BIGINT;
ALTER TABLE medicine_requests ADD CONSTRAINT fk_medicine_request_insurance_card
    FOREIGN KEY (insurance_card_id) REFERENCES insurance_cards(id);