-- 過去の名前を保持し、新規受付では名前を収集しない。過去の種別を推測で補完しない。
ALTER TABLE contacts MODIFY COLUMN name VARCHAR(100) NULL;
ALTER TABLE contacts ADD COLUMN category VARCHAR(30) NULL;
ALTER TABLE contacts ADD CONSTRAINT ck_contacts_category CHECK (
    category IS NULL OR category IN ('USAGE', 'BUG_REPORT', 'ACCOUNT', 'WITHDRAWAL', 'RIGHTS_PRIVACY', 'OTHER')
);
