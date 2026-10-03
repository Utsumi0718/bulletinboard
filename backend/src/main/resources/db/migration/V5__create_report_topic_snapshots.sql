-- 既存Reportの対象所有者は遡って確定できないためNULLを許容する。
-- 新規受付ではサーバー側でIDを設定する。
ALTER TABLE reports ADD COLUMN target_owner_user_id BIGINT NULL;

CREATE TABLE report_topic_snapshots (
    report_id BIGINT PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    question VARCHAR(200) NOT NULL,
    content_type VARCHAR(30) NOT NULL,
    byte_size INT NOT NULL,
    image_content MEDIUMBLOB NOT NULL,
    CONSTRAINT fk_report_topic_snapshots_report FOREIGN KEY (report_id) REFERENCES reports(id)
);
