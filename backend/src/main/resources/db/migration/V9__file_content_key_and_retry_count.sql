-- V9: add columns required by the Elasticsearch indexing pipeline.
--
-- content_key       — MinIO object key (contents/{tenantId}/{fileId}.txt) for the
--                     extracted plain-text version of the file. NULL means either
--                     the MIME type was not extractable or indexing has not run yet.
-- index_retry_count — Number of failed indexing attempts. Bounded by
--                     app.indexing.retry.max-attempts; once reached the retry
--                     scheduler stops re-publishing the file.
--
-- The columns may already exist in dev environments because of
-- spring.jpa.hibernate.ddl-auto=update, so we use ADD COLUMN IF NOT EXISTS to
-- keep the migration idempotent.

ALTER TABLE files ADD COLUMN IF NOT EXISTS content_key VARCHAR(1000);
ALTER TABLE files ADD COLUMN IF NOT EXISTS index_retry_count INTEGER NOT NULL DEFAULT 0;

-- Partial index — only rows that still need indexing live in this index.
-- Makes IndexingRetryService.findByIsIndexedFalseAndIndexRetryCountLessThanAndCreatedAtBefore
-- cheap even when the files table grows to millions of rows.
CREATE INDEX IF NOT EXISTS idx_files_unindexed
    ON files (is_indexed, created_at)
    WHERE is_indexed = false;

COMMENT ON COLUMN files.content_key       IS 'MinIO object key (contents/{tenantId}/{fileId}.txt) for extracted text; NULL if not extractable or not indexed yet';
COMMENT ON COLUMN files.index_retry_count IS 'Number of failed indexing attempts; capped at app.indexing.retry.max-attempts';
