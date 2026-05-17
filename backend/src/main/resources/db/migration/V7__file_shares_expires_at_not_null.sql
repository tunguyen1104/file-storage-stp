-- Share expiration is mandatory. Requests provide expireTime and backend computes expires_at.
ALTER TABLE file_shares
    ALTER COLUMN expires_at SET NOT NULL;

COMMENT ON COLUMN file_shares.expires_at IS 'Required share expiry timestamp computed by backend from request expireTime.';
