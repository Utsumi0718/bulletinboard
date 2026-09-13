CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'ROLE_USER',
    failed_attempt INT NOT NULL DEFAULT 0,
    account_non_locked BOOLEAN NOT NULL DEFAULT TRUE,
    account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL,
    withdrawn_at DATETIME NULL
);

CREATE TABLE profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    icon VARCHAR(255) NULL,
    bio VARCHAR(300) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,

    CONSTRAINT fk_profiles_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
);

CREATE TABLE topics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    image VARCHAR(255) NOT NULL,
    question VARCHAR(200) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    deleted_at DATETIME NULL,

    CONSTRAINT fk_topics_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
);

CREATE TABLE answers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    topic_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content VARCHAR(200) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    deleted_at DATETIME NULL,

    CONSTRAINT fk_answers_topic
        FOREIGN KEY (topic_id)
        REFERENCES topics(id),

    CONSTRAINT fk_answers_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
);

CREATE TABLE likes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    answer_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,

    CONSTRAINT fk_likes_user
        FOREIGN KEY (user_id)
        REFERENCES users(id),

    CONSTRAINT fk_likes_answer
        FOREIGN KEY (answer_id)
        REFERENCES answers(id),

    CONSTRAINT uq_likes_user_answer
        UNIQUE (user_id, answer_id)
);

CREATE TABLE ranking_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    topic_id BIGINT NOT NULL,
    answer_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    checkpoint VARCHAR(20) NOT NULL,
    like_count INT NOT NULL,
    created_at DATETIME NOT NULL,

    CONSTRAINT fk_ranking_results_topic
        FOREIGN KEY (topic_id)
        REFERENCES topics(id),

    CONSTRAINT fk_ranking_results_answer
        FOREIGN KEY (answer_id)
        REFERENCES answers(id),

    CONSTRAINT fk_ranking_results_user
        FOREIGN KEY (user_id)
        REFERENCES users(id),

    CONSTRAINT uq_ranking_results_topic_answer_checkpoint
        UNIQUE (topic_id, answer_id, checkpoint)
);

CREATE TABLE winner_achievements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    topic_id BIGINT NOT NULL,
    answer_id BIGINT NOT NULL,
    like_count INT NOT NULL,
    achieved_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,

    CONSTRAINT fk_winner_achievements_user
        FOREIGN KEY (user_id)
        REFERENCES users(id),

    CONSTRAINT fk_winner_achievements_topic
        FOREIGN KEY (topic_id)
        REFERENCES topics(id),

    CONSTRAINT fk_winner_achievements_answer
        FOREIGN KEY (answer_id)
        REFERENCES answers(id),

    CONSTRAINT uq_winner_achievements_user_topic
        UNIQUE (user_id, topic_id)
);

CREATE TABLE reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reporter_user_id BIGINT NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id BIGINT NOT NULL,
    reason VARCHAR(50) NOT NULL,
    detail VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'UNHANDLED',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,

    CONSTRAINT fk_reports_reporter_user
        FOREIGN KEY (reporter_user_id)
        REFERENCES users(id),

    CONSTRAINT uq_reports_reporter_target
        UNIQUE (reporter_user_id, target_type, target_id)
);

CREATE TABLE contacts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    subject VARCHAR(150) NOT NULL,
    message TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'UNANSWERED',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);


CREATE TABLE x_post_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    topic_id BIGINT NOT NULL,
    answer_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    x_post_id VARCHAR(100) NULL,
    error_message TEXT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,

    CONSTRAINT fk_x_post_history_user
        FOREIGN KEY (user_id)
        REFERENCES users(id),

    CONSTRAINT fk_x_post_history_topic
        FOREIGN KEY (topic_id)
        REFERENCES topics(id),

    CONSTRAINT fk_x_post_history_answer
        FOREIGN KEY (answer_id)
        REFERENCES answers(id),

    CONSTRAINT uq_x_post_history_user_topic
        UNIQUE (user_id, topic_id)
);

-- =========================================================
-- Indexes
-- =========================================================

CREATE INDEX idx_topics_user_id
    ON topics(user_id);

CREATE INDEX idx_topics_created_at
    ON topics(created_at);

CREATE INDEX idx_answers_topic_id
    ON answers(topic_id);

CREATE INDEX idx_answers_user_id
    ON answers(user_id);

CREATE INDEX idx_answers_created_at
    ON answers(created_at);

CREATE INDEX idx_likes_answer_id
    ON likes(answer_id);

CREATE INDEX idx_ranking_results_topic_checkpoint
    ON ranking_results(topic_id, checkpoint);

CREATE INDEX idx_winner_achievements_user_id
    ON winner_achievements(user_id);

CREATE INDEX idx_reports_status_created_at
    ON reports(status, created_at);

CREATE INDEX idx_contacts_status_created_at
    ON contacts(status, created_at);

CREATE INDEX idx_x_post_history_status
    ON x_post_history(status);