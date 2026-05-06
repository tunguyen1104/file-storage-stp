WITH recalculated_usage AS (
    SELECT
        t.id AS tenant_id,
        COALESCE(SUM(
            CASE
                WHEN u.is_active = true THEN u.storage_quota_bytes
                ELSE u.storage_used_bytes
            END
        ), 0) AS expected_storage_used_bytes
    FROM tenants t
    LEFT JOIN users u ON u.tenant_id = t.id
    GROUP BY t.id
)
UPDATE tenants t
SET storage_used_bytes = r.expected_storage_used_bytes
FROM recalculated_usage r
WHERE t.id = r.tenant_id
  AND t.storage_used_bytes IS DISTINCT FROM r.expected_storage_used_bytes;
