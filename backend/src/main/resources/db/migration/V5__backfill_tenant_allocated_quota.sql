UPDATE tenants t
SET storage_used_bytes = COALESCE((
    SELECT SUM(
        CASE
            WHEN u.is_active = true THEN u.storage_quota_bytes
            ELSE u.storage_used_bytes
        END
    )
    FROM users u
    WHERE u.tenant_id = t.id
), 0);
