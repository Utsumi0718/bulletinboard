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
    '猫のお題',
    'cat.webp',
    'この猫、何を考えてる？',
    '2026-08-28 10:00:00',
    '2026-08-28 10:00:00',
    NULL
),
(
    2,
    1,
    '犬のお題',
    'dog.webp',
    'この犬が言いそうな一言は？',
    '2026-08-28 11:00:00',
    '2026-08-28 11:00:00',
    NULL
),
(
    3,
    1,
    '削除済みのお題',
    'deleted.webp',
    '削除済みです',
    '2026-08-28 12:00:00',
    '2026-08-28 12:00:00',
    '2026-08-28 13:00:00'
);