-- Chat RAG manual schema.
--
-- This file is intentionally placed under docs/sql, not src/main/resources/db/migration.
-- Phase 1 does not add Flyway/Liquibase. Run this SQL manually on the target
-- PostgreSQL database before enabling RAG code paths.

CREATE TABLE IF NOT EXISTS file_rag_documents (
  file_id uuid PRIMARY KEY REFERENCES files(id),
  status varchar(20) NOT NULL,
  source_key varchar(1000),
  source_type varchar(50) NOT NULL DEFAULT 'EXTRACTED_TEXT',
  chunk_count integer NOT NULL DEFAULT 0,
  embedding_model varchar(200),
  embedding_dimensions integer,
  chunker_version varchar(50),
  active_ingestion_id uuid,
  retry_count integer NOT NULL DEFAULT 0,
  last_error text,
  indexed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT chk_file_rag_documents_status CHECK (
    status IN (
      'PENDING',
      'INDEXING',
      'INDEXED',
      'FAILED',
      'UNSUPPORTED',
      'PENDING_DELETE',
      'DELETED'
    )
  ),
  CONSTRAINT chk_file_rag_documents_source_type CHECK (
    source_type IN ('EXTRACTED_TEXT')
  ),
  CONSTRAINT chk_file_rag_documents_chunk_count CHECK (chunk_count >= 0),
  CONSTRAINT chk_file_rag_documents_retry_count CHECK (retry_count >= 0),
  CONSTRAINT chk_file_rag_documents_embedding_dimensions CHECK (
    embedding_dimensions IS NULL OR embedding_dimensions > 0
  )
);

CREATE INDEX IF NOT EXISTS idx_file_rag_documents_status
  ON file_rag_documents(status);

CREATE INDEX IF NOT EXISTS idx_file_rag_documents_retry
  ON file_rag_documents(status, updated_at)
  WHERE status IN ('PENDING', 'FAILED', 'INDEXING');

CREATE TABLE IF NOT EXISTS rag_chat_sessions (
  id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL REFERENCES tenants(id),
  user_id uuid NOT NULL REFERENCES users(id),
  title varchar(255),
  status varchar(20) NOT NULL DEFAULT 'ACTIVE',
  last_message_at timestamptz,
  deleted_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT chk_rag_chat_sessions_status CHECK (
    status IN ('ACTIVE', 'DELETED')
  )
);

CREATE INDEX IF NOT EXISTS idx_rag_chat_sessions_user_updated
  ON rag_chat_sessions(user_id, updated_at DESC)
  WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_rag_chat_sessions_tenant_user
  ON rag_chat_sessions(tenant_id, user_id);

CREATE TABLE IF NOT EXISTS rag_chat_messages (
  id uuid PRIMARY KEY,
  session_id uuid NOT NULL REFERENCES rag_chat_sessions(id),
  role varchar(20) NOT NULL,
  content text NOT NULL,
  model varchar(200),
  retrieval_top_k integer,
  input_tokens integer,
  output_tokens integer,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT chk_rag_chat_messages_role CHECK (
    role IN ('USER', 'ASSISTANT')
  ),
  CONSTRAINT chk_rag_chat_messages_retrieval_top_k CHECK (
    retrieval_top_k IS NULL OR retrieval_top_k > 0
  ),
  CONSTRAINT chk_rag_chat_messages_input_tokens CHECK (
    input_tokens IS NULL OR input_tokens >= 0
  ),
  CONSTRAINT chk_rag_chat_messages_output_tokens CHECK (
    output_tokens IS NULL OR output_tokens >= 0
  )
);

CREATE INDEX IF NOT EXISTS idx_rag_chat_messages_session_created
  ON rag_chat_messages(session_id, created_at ASC);

CREATE TABLE IF NOT EXISTS rag_chat_message_sources (
  id uuid PRIMARY KEY,
  message_id uuid NOT NULL REFERENCES rag_chat_messages(id),
  file_id uuid NOT NULL REFERENCES files(id),
  original_name varchar(500) NOT NULL,
  chunk_index integer NOT NULL,
  snippet text,
  score double precision,
  source_key varchar(1000),
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT chk_rag_chat_message_sources_chunk_index CHECK (chunk_index >= 0)
);

CREATE INDEX IF NOT EXISTS idx_rag_chat_message_sources_message
  ON rag_chat_message_sources(message_id);

CREATE INDEX IF NOT EXISTS idx_rag_chat_message_sources_file
  ON rag_chat_message_sources(file_id);

COMMENT ON TABLE file_rag_documents IS 'Per-file RAG indexing state. Elasticsearch rag_chunks stores the actual chunk/vector documents.';
COMMENT ON COLUMN file_rag_documents.active_ingestion_id IS 'Current successful RAG ingestion id. Reindex writes a new ingestion before deleting old chunks.';
COMMENT ON TABLE rag_chat_sessions IS 'RAG chat sessions owned by one user in one tenant.';
COMMENT ON TABLE rag_chat_messages IS 'RAG chat messages. Phase 1 stores USER and ASSISTANT messages only.';
COMMENT ON TABLE rag_chat_message_sources IS 'Snapshot citations for assistant messages. Source visibility is rechecked when reading chat history.';
