-- Add lifecycle columns for share records.
ALTER TABLE file_shares
    ADD COLUMN status     VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN revoked_at TIMESTAMPTZ,
    ADD COLUMN updated_at TIMESTAMPTZ DEFAULT NOW();

-- File shares and tenant-wide shares do not carry a configurable permission.
ALTER TABLE file_shares ALTER COLUMN permission DROP DEFAULT;
ALTER TABLE file_shares ALTER COLUMN permission DROP NOT NULL;

-- Backfill existing rows created before status existed.
UPDATE file_shares SET status = 'ACTIVE' WHERE status IS NULL;

-- Indexes for individual shares.
CREATE INDEX idx_file_shares_shared_by
    ON file_shares(shared_by, status);

CREATE INDEX idx_file_shares_shared_with
    ON file_shares(shared_with_user_id, status);

CREATE INDEX idx_file_shares_file_user
    ON file_shares(file_id, shared_with_user_id, status);

CREATE INDEX idx_file_shares_folder_user
    ON file_shares(folder_id, shared_with_user_id, status);

-- Prevent duplicate active individual shares for the same resource and user.
CREATE UNIQUE INDEX uq_file_shares_file_user_active
    ON file_shares(file_id, shared_with_user_id)
    WHERE status = 'ACTIVE' AND file_id IS NOT NULL AND shared_with_user_id IS NOT NULL;

CREATE UNIQUE INDEX uq_file_shares_folder_user_active
    ON file_shares(folder_id, shared_with_user_id)
    WHERE status = 'ACTIVE' AND folder_id IS NOT NULL AND shared_with_user_id IS NOT NULL;

-- Indexes for tenant-wide shares.
CREATE INDEX idx_file_shares_file_tenant
    ON file_shares(file_id, shared_with_tenant_id, status);

CREATE INDEX idx_file_shares_folder_tenant
    ON file_shares(folder_id, shared_with_tenant_id, status);

-- Prevent duplicate active tenant-wide shares for the same resource and tenant.
CREATE UNIQUE INDEX uq_file_shares_file_tenant_active
    ON file_shares(file_id, shared_with_tenant_id)
    WHERE status = 'ACTIVE' AND file_id IS NOT NULL;

CREATE UNIQUE INDEX uq_file_shares_folder_tenant_active
    ON file_shares(folder_id, shared_with_tenant_id)
    WHERE status = 'ACTIVE' AND folder_id IS NOT NULL;

-- Support active-share expiration checks.
CREATE INDEX idx_file_shares_expires
    ON file_shares(expires_at)
    WHERE status = 'ACTIVE';
