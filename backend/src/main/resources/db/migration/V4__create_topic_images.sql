-- 原本は追記のみ。Topicの削除・差し替えで画像本体を連動削除しない。
-- 通報証拠のテーブルはD-2の受付実装時に別途追加する。
CREATE TABLE topic_images (
    id VARCHAR(36) PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    content_type VARCHAR(30) NOT NULL,
    byte_size INT NOT NULL,
    content MEDIUMBLOB NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_topic_images_owner FOREIGN KEY (owner_user_id) REFERENCES users(id)
);

-- 取得可否の確認で公開中Topicから画像URLを検索する。
CREATE INDEX idx_topics_image_deleted_at ON topics(image, deleted_at);
