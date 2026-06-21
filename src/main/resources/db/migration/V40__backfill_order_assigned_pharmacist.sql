-- Per-pharmacist reports filter orders by orders.assigned_pharmacist_id, but the dispensing
-- flow only logged actions in pharmacist_action_logs and never set assigned_pharmacist_id.
-- Backfill it from the earliest action log per order (the first pharmacist who acted),
-- matching the runtime first-toucher attribution now applied in DispensingService.logAction.
UPDATE orders o
SET assigned_pharmacist_id = pick.pharmacist_profile_id
FROM (
    SELECT DISTINCT ON (order_id) order_id, pharmacist_profile_id
    FROM pharmacist_action_logs
    ORDER BY order_id, timestamp ASC
) pick
WHERE o.id = pick.order_id
  AND o.assigned_pharmacist_id IS NULL;
