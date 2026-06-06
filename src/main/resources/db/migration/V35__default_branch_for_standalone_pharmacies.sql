-- Create a default "Main Branch" for every active pharmacy that has no branches yet
INSERT INTO branches (name, address, latitude, longitude, contact_info, status, pharmacy_id, created_at)
SELECT
    p.name || ' - Main Branch',
    p.address,
    p.latitude,
    p.longitude,
    p.contact_info,
    'ACTIVE',
    p.id,
    NOW()
FROM pharmacies p
WHERE NOT EXISTS (SELECT 1 FROM branches b WHERE b.pharmacy_id = p.id)
  AND p.status = 'ACTIVE';
