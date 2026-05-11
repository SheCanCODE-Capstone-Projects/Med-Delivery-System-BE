-- Create pharmacist_sequences table for generating unique pharmacist IDs
CREATE TABLE IF NOT EXISTS pharmacist_sequences (
    pharmacy_id BIGINT PRIMARY KEY,
    last_number BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_pharmacist_sequences_pharmacy
        FOREIGN KEY (pharmacy_id)
        REFERENCES pharmacies(id)
        ON DELETE CASCADE
);

-- Create index for faster lookups
CREATE INDEX IF NOT EXISTS idx_pharmacist_sequences_pharmacy_id ON pharmacist_sequences(pharmacy_id);
