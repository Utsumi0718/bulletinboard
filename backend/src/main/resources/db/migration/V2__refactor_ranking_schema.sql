-- =========================================================
-- Ranking Schema Refactor
-- =========================================================

-- 旧ランキング設計のテーブルを削除する
-- 現在はランキング関連データが0件のため、
-- データ移行は行わず新しい構造で作り直す
DROP TABLE ranking_results;
DROP TABLE winner_achievements;

-- 王者判定そのものの履歴を保存するテーブル
CREATE TABLE ranking_judgments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    topic_id BIGINT NOT NULL,
    checkpoint VARCHAR(20) NOT NULL,
    judged_at DATETIME NOT NULL,

    CONSTRAINT fk_ranking_judgments_topic
        FOREIGN KEY (topic_id)
        REFERENCES topics(id),

    CONSTRAINT uq_ranking_judgments_topic_checkpoint
        UNIQUE (topic_id, checkpoint)
);

-- 各王者判定で王者になった回答を保存するテーブル
CREATE TABLE ranking_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    answer_id BIGINT NOT NULL,
    ranking_judgment_id BIGINT NOT NULL,
    like_count INT NOT NULL,

    CONSTRAINT fk_ranking_results_answer
        FOREIGN KEY (answer_id)
        REFERENCES answers(id),

    CONSTRAINT fk_ranking_results_ranking_judgment
        FOREIGN KEY (ranking_judgment_id)
        REFERENCES ranking_judgments(id),

    CONSTRAINT uq_ranking_results_ranking_judgment_answer
        UNIQUE (ranking_judgment_id, answer_id)
);


-- ユーザーがTopicごとに初めて王者になった実績を保存するテーブル
CREATE TABLE winner_achievements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    topic_id BIGINT NOT NULL,
    answer_id BIGINT NOT NULL,
    like_count INT NOT NULL,
    achieved_at DATETIME NOT NULL,

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

CREATE INDEX idx_winner_achievements_user_id
    ON winner_achievements(user_id);