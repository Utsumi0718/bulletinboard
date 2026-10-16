-- Profile画像原本。証拠画像はreport_profile_snapshotsへコピーし、元画像と連動削除しない。
CREATE TABLE profile_images (
    id VARCHAR(36) PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    content_type VARCHAR(30) NOT NULL,
    byte_size INT NOT NULL,
    content MEDIUMBLOB NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_profile_images_owner FOREIGN KEY (owner_user_id) REFERENCES users(id),
    CONSTRAINT ck_profile_images_size CHECK (byte_size > 0 AND byte_size <= 2000000)
);

CREATE INDEX idx_profiles_icon ON profiles(icon);
