-- インスタンス間で共有する10分移動窓。生のIPやお問い合わせ内容を保存しない。
CREATE TABLE contact_rate_limit_buckets (
    source_key VARCHAR(64) PRIMARY KEY,
    expires_at DATETIME(6) NOT NULL,
    slot1 DATETIME(6) NULL,
    slot2 DATETIME(6) NULL,
    slot3 DATETIME(6) NULL,
    slot4 DATETIME(6) NULL,
    slot5 DATETIME(6) NULL
);
CREATE INDEX idx_contact_rate_expiry ON contact_rate_limit_buckets(expires_at);
