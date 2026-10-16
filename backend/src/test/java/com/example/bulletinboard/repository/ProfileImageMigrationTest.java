package com.example.bulletinboard.repository;

import static org.assertj.core.api.Assertions.*;
import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** V9単独のSQLと制約を専用H2で確認する。MySQL適用済みとは扱わない。 */
class ProfileImageMigrationTest {
    @Test void newMigrationCreatesOriginalTableAndPreservesExistingProfileReference() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:migration-" + UUID.randomUUID() + ";MODE=MySQL")) {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE users (id BIGINT PRIMARY KEY)");
                statement.execute("CREATE TABLE profiles (id BIGINT PRIMARY KEY, icon VARCHAR(255))");
                statement.execute("INSERT INTO users VALUES (1)");
                statement.execute("INSERT INTO profiles VALUES (1, 'legacy-reference')");
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V9__create_profile_images.sql"));
                try (var row = statement.executeQuery("SELECT icon FROM profiles WHERE id = 1")) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getString(1)).isEqualTo("legacy-reference");
                }
                statement.execute("INSERT INTO profile_images VALUES ('valid', 1, 'image/png', 1, X'01', CURRENT_TIMESTAMP)");
                assertThatThrownBy(() -> statement.execute("INSERT INTO profile_images VALUES ('large', 1, 'image/png', 2000001, X'01', CURRENT_TIMESTAMP)"))
                        .isInstanceOf(java.sql.SQLException.class);
                assertThatThrownBy(() -> statement.execute("INSERT INTO profile_images VALUES ('foreign', 999, 'image/png', 1, X'01', CURRENT_TIMESTAMP)"))
                        .isInstanceOf(java.sql.SQLException.class);
            }
        }
    }
}
