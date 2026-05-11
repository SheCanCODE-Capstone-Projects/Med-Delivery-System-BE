-- Drop the old constraint that requires prescription_id OR medicine_request_id
ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_order_source;

-- Add new constraint that allows PRIVATE_PURCHASE without prescription/medicine_request
-- but requires one of them for PRESCRIPTION_BASED orders
ALTER TABLE orders ADD CONSTRAINT chk_order_source CHECK (
    (order_type = 'PRIVATE_PURCHASE')
    OR 
    (order_type = 'PRESCRIPTION_BASED' AND prescription_id IS NOT NULL)
    OR
    (medicine_request_id IS NOT NULL)
);
