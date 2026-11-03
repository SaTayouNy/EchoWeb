ALTER TABLE media
ADD COLUMN sync_status VARCHAR(16) NOT NULL DEFAULT 'SYNCED';

CREATE INDEX idx_media_sync_status_last_synced_at ON media (sync_status, last_synced_at);
