-- V3: Remove redundant tenant_id column from multipart_upload_sessions.
-- The table was created in V1__initial_schema.sql.
-- Tenant isolation is enforced via the file → tenant relationship;
-- ownership checks use initiated_by (userId from JWT) directly.

ALTER TABLE multipart_upload_sessions
DROP COLUMN IF EXISTS tenant_id;
