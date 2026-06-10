-- Replace empty-string phone_number with NULL so the UNIQUE constraint
-- allows multiple users without a phone (empty string ≠ NULL in SQL).
UPDATE users SET phone_number = NULL WHERE phone_number = '';
