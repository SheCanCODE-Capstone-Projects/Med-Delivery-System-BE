-- Migration: V25__create_cache_table.sql
-- Use PostgreSQL as cache instead of Redis

CREATE TABLE IF NOT EXISTS cache_entries (
    cache_key VARCHAR(255) PRIMARY KEY,
    cache_value TEXT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Index for cleanup of expired entries
CREATE INDEX idx_cache_expires_at ON cache_entries(expires_at);

-- Auto-cleanup function (runs periodically)
CREATE OR REPLACE FUNCTION cleanup_expired_cache()
RETURNS void AS $$
BEGIN
    DELETE FROM cache_entries WHERE expires_at < NOW();
END;
$$ LANGUAGE plpgsql;
