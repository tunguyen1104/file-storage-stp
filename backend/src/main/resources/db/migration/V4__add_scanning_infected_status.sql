-- Drop the existing status check constraint and re-create it with SCANNING and INFECTED values
ALTER TABLE "files" DROP CONSTRAINT IF EXISTS "files_status_check";

ALTER TABLE "files"
    ADD CONSTRAINT "files_status_check"
    CHECK (status IN ('PENDING', 'SCANNING', 'ACTIVE', 'PENDING_DELETE', 'DELETED', 'UPLOAD_FAILED', 'INFECTED'));

