package com.example.bulletinboard.repository;

import static org.assertj.core.api.Assertions.*;
import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** V10〜V12単独のSQLを隔離H2で確認する。実MySQL適用の検証ではない。 */
class ContactMigrationTest {
    private Connection migrated() throws Exception {
        var c=DriverManager.getConnection("jdbc:h2:mem:contact-migration-"+UUID.randomUUID()+";MODE=MySQL");
        c.createStatement().execute("CREATE TABLE contacts (id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL, status VARCHAR(20), created_at DATETIME)");
        c.createStatement().execute("INSERT INTO contacts VALUES (1, '旧名前', 'UNANSWERED', CURRENT_TIMESTAMP)");
        for (String script : new String[]{"V10__update_contact_submission.sql", "V11__create_contact_rate_limit_buckets.sql", "V12__add_contact_retention.sql"})
            ScriptUtils.executeSqlScript(c, new ClassPathResource("db/migration/"+script));
        return c;
    }
    @Test void keepsOldNameAndUnknownCategoryWithoutInventingData() throws Exception {
        try(var c=migrated(); var s=c.createStatement(); var r=s.executeQuery("SELECT name, category, legal_hold_until FROM contacts WHERE id=1")) {
            assertThat(r.next()).isTrue(); assertThat(r.getString(1)).isEqualTo("旧名前");
            assertThat(r.getString(2)).isNull(); assertThat(r.getObject(3)).isNull();
        }
    }
    @Test void acceptsNewNamelessRowsAndRestrictsNonNullCategory() throws Exception {
        try(var c=migrated(); var s=c.createStatement()) {
            s.execute("INSERT INTO contacts(id,category) VALUES(2,'BUG_REPORT')");
            assertThatThrownBy(()->s.execute("INSERT INTO contacts(id,category) VALUES(3,'UNKNOWN')")).isInstanceOf(SQLException.class);
            s.execute("INSERT INTO contact_rate_limit_buckets(source_key,expires_at) VALUES('key',CURRENT_TIMESTAMP)");
            s.execute("INSERT INTO contact_retention_logs(contact_id,status,deleted_at) VALUES(999,'RESOLVED',CURRENT_TIMESTAMP)");
        }
    }
}
