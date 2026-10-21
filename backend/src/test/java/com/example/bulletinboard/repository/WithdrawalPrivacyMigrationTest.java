package com.example.bulletinboard.repository;

import static org.assertj.core.api.Assertions.*;
import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** V13を独立H2へ適用。既存退会者の値を変更しないことを確認する。実MySQLは別確認。 */
class WithdrawalPrivacyMigrationTest {
    @Test void migrationAddsManagementSchemaWithoutErasingExistingIdentity() throws Exception {
        try(Connection connection=DriverManager.getConnection("jdbc:h2:mem:privacy-migration-"+UUID.randomUUID()+";MODE=MySQL")) {
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V1__create_initial_schema.sql"));
            try(Statement sql=connection.createStatement()) {
                sql.execute("INSERT INTO users(id,username,email,password,role,account_status,created_at) VALUES(1,'legacy123','fictional@example.test','old-hash','ROLE_USER','WITHDRAWN',CURRENT_TIMESTAMP)");
                ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V13__withdrawal_privacy_management.sql"));
                try(ResultSet result=sql.executeQuery("SELECT email,password,privacy_reviewer,authentication_version,personal_data_erased_at FROM users WHERE id=1")) {
                    assertThat(result.next()).isTrue();assertThat(result.getString(1)).isEqualTo("fictional@example.test");
                    assertThat(result.getString(2)).isEqualTo("old-hash");assertThat(result.getBoolean(3)).isFalse();
                    assertThat(result.getLong(4)).isZero();assertThat(result.getObject(5)).isNull();
                }
                sql.execute("INSERT INTO withdrawal_tasks(user_id,session_status,image_status,created_at,updated_at) VALUES(1,'PENDING','POLICY_PENDING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
                assertThatThrownBy(() -> sql.execute("DELETE FROM users WHERE id=1")).isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> sql.execute("INSERT INTO users(username,email,password,role,created_at) VALUES('legacy123','other@example.test','x','ROLE_USER',CURRENT_TIMESTAMP)")).isInstanceOf(SQLException.class);
            }
        }
    }
}
