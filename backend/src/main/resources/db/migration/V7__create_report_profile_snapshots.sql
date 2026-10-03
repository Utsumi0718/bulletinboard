CREATE TABLE report_profile_snapshots (
    report_id BIGINT PRIMARY KEY,
    display_name VARCHAR(50) NOT NULL,
    bio VARCHAR(300) NULL,
    content_type VARCHAR(30) NULL,
    image_content MEDIUMBLOB NULL,
    CONSTRAINT fk_report_profile_snapshots_report FOREIGN KEY (report_id) REFERENCES reports(id)
);
