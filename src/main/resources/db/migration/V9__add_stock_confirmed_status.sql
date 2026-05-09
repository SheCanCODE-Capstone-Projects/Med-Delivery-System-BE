ALTER TABLE orders
    DROP CONSTRAINT IF EXISTS chk_order_status;

ALTER TABLE orders
    ADD CONSTRAINT chk_order_status CHECK (
        status IN (
            'UPLOADED',
            'MATCHING',
            'ASSIGNED',
            'IN_PROGRESS',
            'STOCK_CONFIRMED',
            'READY_FOR_PICKUP',
            'OUT_FOR_DELIVERY',
            'COMPLETED',
            'CANCELLED'
        )
    );
