-- =========================================================
-- 管理者の操作履歴
-- =========================================================
-- 状態変更・削除と同じトランザクションで保存します。
-- 名前・メールアドレス・件名・本文は記録しません。

CREATE TABLE admin_operation_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_user_id BIGINT NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id BIGINT NOT NULL,
    action VARCHAR(30) NOT NULL,
    before_status VARCHAR(20) NOT NULL,
    after_status VARCHAR(20) NULL,
    result VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL,

    -- 操作した管理者を参照します。
    -- ユーザー削除に連動して履歴を削除する設定は付けません。
    CONSTRAINT fk_admin_operation_logs_admin_user
        FOREIGN KEY (admin_user_id)
        REFERENCES users(id)
);

-- 対象ごとの操作履歴を日時順で検索するためのインデックス。
-- target_idには外部キーを付けず、
-- お問い合わせの物理削除後も履歴を保持します。
CREATE INDEX idx_admin_operation_logs_target_created_at
    ON admin_operation_logs(target_type, target_id, created_at);