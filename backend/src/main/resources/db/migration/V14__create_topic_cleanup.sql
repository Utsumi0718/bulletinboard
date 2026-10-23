-- V13 is reserved by the unmerged withdrawal privacy change. Coordinate migration
-- ordering before deployment; never enable out-of-order migration automatically.
ALTER TABLE topics ADD COLUMN cleanup_deleted_at_utc DATETIME(6) NULL;
CREATE INDEX idx_topics_cleanup_deleted ON topics(cleanup_deleted_at_utc, id);
CREATE INDEX idx_topics_deleted_id ON topics(deleted_at, id);
CREATE INDEX idx_reports_target_status ON reports(target_type, target_id, status);
ALTER TABLE admin_operation_logs ADD COLUMN reason VARCHAR(500) NULL;

CREATE TABLE ranking_result_archives (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    original_topic_id BIGINT NOT NULL,
    original_answer_id BIGINT NOT NULL,
    checkpoint VARCHAR(20) NOT NULL,
    like_count INT NOT NULL,
    judged_at DATETIME(6) NOT NULL,
    archived_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_ranking_archive_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uq_ranking_archive UNIQUE(original_topic_id, checkpoint, original_answer_id)
);
CREATE TABLE winner_achievement_archives (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    original_topic_id BIGINT NOT NULL,
    original_answer_id BIGINT NOT NULL,
    like_count INT NOT NULL,
    achieved_at DATETIME(6) NOT NULL,
    archived_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_winner_archive_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uq_winner_archive UNIQUE(user_id, original_topic_id)
);
CREATE TABLE cleanup_job_leases (
    job_key VARCHAR(50) PRIMARY KEY,
    owner_id VARCHAR(36) NULL,
    expires_at DATETIME(6) NOT NULL,
    generation BIGINT NOT NULL
);
INSERT INTO cleanup_job_leases VALUES ('TOPIC_CLEANUP', NULL, '1970-01-01 00:00:00', 0);
CREATE TABLE topic_cleanup_runs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    scheduled_date DATE NOT NULL UNIQUE,
    owner_id VARCHAR(36) NOT NULL,
    generation BIGINT NOT NULL,
    started_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    status VARCHAR(24) NOT NULL,
    daily_limit INT NOT NULL,
    processed_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failure_count INT NOT NULL DEFAULT 0,
    held_count INT NOT NULL DEFAULT 0
);
CREATE TABLE topic_cleanup_targets (
    original_topic_id BIGINT PRIMARY KEY,
    state VARCHAR(24) NOT NULL,
    reason_code VARCHAR(40) NULL,
    failure_count INT NOT NULL DEFAULT 0,
    cycle_no INT NOT NULL DEFAULT 0,
    last_checked_at DATETIME(6) NULL,
    next_eligible_date DATE NULL,
    deleted_success_at DATETIME(6) NULL,
    pending_image_id VARCHAR(36) NULL,
    pending_image_owner_id BIGINT NULL
);
CREATE INDEX idx_cleanup_target_state ON topic_cleanup_targets(state, last_checked_at);
CREATE TABLE topic_cleanup_attempts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id BIGINT NOT NULL,
    original_topic_id BIGINT NOT NULL,
    cycle_no INT NOT NULL,
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    outcome VARCHAR(24) NOT NULL,
    reason_code VARCHAR(40) NULL,
    error_type VARCHAR(100) NULL,
    CONSTRAINT fk_cleanup_attempt_run FOREIGN KEY(run_id) REFERENCES topic_cleanup_runs(id),
    CONSTRAINT uq_cleanup_attempt UNIQUE(run_id, original_topic_id)
);
