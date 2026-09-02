DELETE FROM answers;
DELETE FROM topics;
DELETE FROM users;

INSERT INTO users (
    id,
    username,
    email,
    password,
    role,
    failed_attempt,
    account_non_locked,
    account_status,
    created_at,
    withdrawn_at
)
VALUES
(
    1,
    'testuser',
    'test@example.com',
    'test-password',
    'ROLE_USER',
    0,
    TRUE,
    'ACTIVE',
    '2026-08-28 10:00:00',
    NULL
);

INSERT INTO topics (
    id,
    user_id,
    title,
    image,
    question,
    created_at,
    updated_at,
    deleted_at
)
VALUES
(
    1,
    1,
    'テストお題',
    'test.webp',
    'この写真で一言',
    '2026-08-28 10:00:00',
    '2026-08-28 10:00:00',
    NULL
);

INSERT INTO answers (
    id,
    topic_id,
    user_id,
    content,
    created_at,
    updated_at,
    deleted_at
)
VALUES
(
    1,
    1,
    1,
    'テスト回答1',
    '2026-08-28 11:00:00',
    '2026-08-28 11:00:00',
    NULL
),
(
    2,
    1,
    1,
    'テスト回答2',
    '2026-08-28 11:10:00',
    '2026-08-28 11:10:00',
    NULL
),
(
    3,
    1,
    1,
    '削除済み回答',
    '2026-08-28 11:20:00',
    '2026-08-28 11:20:00',
    '2026-08-28 12:00:00'
);