-- 一旦データをクリアする（重複エラー防止）
DELETE FROM posts;

-- カラム名をスネークケース（created_at, updated_at）に修正
INSERT INTO posts (id, title, content, created_at, updated_at)
VALUES
(1, 'テストタイトル1', 'テスト内容1', '2026-07-23 10:00:00', '2026-07-23 10:00:00'),
(2, 'テストタイトル2', 'テスト内容2', '2026-07-23 11:00:00', '2026-07-23 11:00:00');