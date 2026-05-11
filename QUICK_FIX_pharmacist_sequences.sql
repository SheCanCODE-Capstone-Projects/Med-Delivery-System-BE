-- Quick Fix: Run this SQL directly in PostgreSQL if you don't want to restart the app

CREATE TABLE IF NOT EXISTS pharmacist_sequences (
    pharmacy_id BIGINT PRIMARY KEY,
    last_number BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_pharmacist_sequences_pharmacy
        FOREIGN KEY (pharmacy_id)
        REFERENCES pharmacies(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pharmacist_sequences_pharmacy_id ON pharmacist_sequences(pharmacy_id);

-- Verify the table was created
SELECT * FROM pharmacist_sequences;
