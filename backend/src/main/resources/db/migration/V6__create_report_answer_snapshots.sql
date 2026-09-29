CREATE TABLE report_answer_snapshots (
    report_id BIGINT PRIMARY KEY,
    answer_content VARCHAR(200) NOT NULL,
    topic_id BIGINT NOT NULL,
    topic_title VARCHAR(100) NOT NULL,
    topic_question VARCHAR(200) NOT NULL,
    content_type VARCHAR(30) NOT NULL,
    byte_size INT NOT NULL,
    image_content MEDIUMBLOB NOT NULL,
    CONSTRAINT fk_report_answer_snapshots_report FOREIGN KEY (report_id) REFERENCES reports(id)
);
