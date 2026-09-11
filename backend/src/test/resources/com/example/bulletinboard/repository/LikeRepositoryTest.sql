DELETE FROM likes;
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
    'answer_owner',
    'owner@example.com',
    'test-password',
    'ROLE_USER',
    0,
    TRUE,
    'ACTIVE',
    '2026-08-28 10:00:00',
    NULL
),
(
    2,
    'like_user_1',
    'like1@example.com',
    'test-password',
    'ROLE_USER',
    0,
    TRUE,
    'ACTIVE',
    '2026-08-28 10:01:00',
    NULL
),
(
    3,
    'like_user_2',
    'like2@example.com',
    'test-password',
    'ROLE_USER',
    0,
    TRUE,
    'ACTIVE',
    '2026-08-28 10:02:00',
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
    'Likeテスト用お題',
    'test.webp',
    'この写真で一言',
    '2026-08-28 10:10:00',
    '2026-08-28 10:10:00',
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
    'Likeが2件ある回答',
    '2026-08-28 11:00:00',
    '2026-08-28 11:00:00',
    NULL
),
(
    2,
    1,
    1,
    'Likeが1件ある回答',
    '2026-08-28 11:10:00',
    '2026-08-28 11:10:00',
    NULL
),
(
    3,
    1,
    1,
    'Likeが0件の回答',
    '2026-08-28 11:20:00',
    '2026-08-28 11:20:00',
    NULL
);


INSERT INTO likes (
    id,
    user_id,
    answer_id,
    created_at
)
VALUES
(
    1,
    2,
    1,
    '2026-08-28 12:00:00'
),
(
    2,
    3,
    1,
    '2026-08-28 12:01:00'
),
(
    3,
    3,
    2,
    '2026-08-28 12:02:00'
);