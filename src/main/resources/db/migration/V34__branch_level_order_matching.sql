-- Add assigned_branch_id to orders so each order is matched to a specific branch,
-- not just the pharmacy as a whole. Existing orders get NULL (no assigned branch yet).
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS assigned_branch_id BIGINT,
    ADD CONSTRAINT fk_order_assigned_branch
        FOREIGN KEY (assigned_branch_id) REFERENCES branches(id) ON DELETE SET NULL;
