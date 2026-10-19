ALTER TABLE contacts ADD COLUMN legal_hold_until DATETIME NULL;
ALTER TABLE contacts ADD COLUMN legal_hold_reason VARCHAR(500) NULL;
CREATE INDEX idx_contacts_retention ON contacts(status, created_at, id);
-- Contactへの外部キーを設けず、対象削除後も成功記録を保持する。
CREATE TABLE contact_retention_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    contact_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    deleted_at DATETIME NOT NULL
);
