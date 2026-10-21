-- スキーマの追加のみ。既存Userの匿名化・保持データの移行は実行しない。
ALTER TABLE users ADD COLUMN privacy_reviewer BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN personal_data_erased_at DATETIME(6) NULL;
ALTER TABLE users ADD COLUMN authentication_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE privacy_retention_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_id VARCHAR(36) NOT NULL,
    report_id BIGINT NULL,
    purpose VARCHAR(500) NULL,
    basis VARCHAR(500) NULL,
    expires_at DATETIME(6) NULL,
    access_scope VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    identity_fields VARCHAR(50) NULL,
    encrypted_payload MEDIUMBLOB NULL,
    key_id VARCHAR(80) NULL,
    created_by BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_privacy_retention_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_privacy_retention_report FOREIGN KEY (report_id) REFERENCES reports(id),
    CONSTRAINT fk_privacy_retention_actor FOREIGN KEY (created_by) REFERENCES users(id)
);
CREATE INDEX idx_privacy_retention_target ON privacy_retention_records(target_type, target_id, status);

CREATE TABLE privacy_audit_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_user_id BIGINT NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_id VARCHAR(36) NOT NULL,
    action VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_privacy_audit_actor FOREIGN KEY (actor_user_id) REFERENCES users(id)
);

CREATE TABLE withdrawal_tasks (
    user_id BIGINT PRIMARY KEY,
    session_status VARCHAR(30) NOT NULL,
    image_status VARCHAR(30) NOT NULL,
    session_attempts INT NOT NULL DEFAULT 0,
    image_attempts INT NOT NULL DEFAULT 0,
    last_error_type VARCHAR(100) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_withdrawal_tasks_user FOREIGN KEY (user_id) REFERENCES users(id)
);
