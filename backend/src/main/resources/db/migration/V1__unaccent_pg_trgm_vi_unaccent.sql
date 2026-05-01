-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- vi_unaccent: replace Vietnamese 'đ/Đ' with 'd/D' then run unaccent()
CREATE OR REPLACE FUNCTION vi_unaccent(text) RETURNS text AS $$
BEGIN
    RETURN unaccent(replace(replace($1, 'đ', 'd'), 'Đ', 'D'));
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- Indexes to accelerate LIKE %...% searches using trigram GIN
CREATE INDEX IF NOT EXISTS idx_files_original_name_trgm
    ON files USING gin (lower(original_name) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_files_original_name_unaccent_trgm
    ON files USING gin (vi_unaccent(lower(original_name)) gin_trgm_ops);

