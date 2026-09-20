package com.example.bulletinboard.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.example.bulletinboard.model.AdminOperationLog;
import com.example.bulletinboard.model.User;

/**
 * 【クラスの役割】
 * 管理者の操作履歴を保存し、DBから取得できることを検証します。
 *
 * 【検証内容】
 * - 操作した管理者との関連
 * - 対象種類・対象ID・操作内容
 * - 変更前後のステータス・結果・作成日時
 *
 * 【テストの範囲】
 * H2と実際のEntity・Repositoryを使用します。
 * 保存後に永続化コンテキストをクリアし、
 * DBから読み直した値を確認します。
 *
 * MySQL固有の動作、FlywayのMigration、
 * 管理操作と履歴保存の同時コミットは別途確認します。
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=never"
})
class AdminOperationLogRepositoryTest {

    @Autowired
    private AdminOperationLogRepository operationLogRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("状態変更の操作履歴を保存し、DBから取得できる")
    void save_shouldPersistStatusChangeLog() {

        // 外部キーの参照先となる管理者を保存する
        User admin = new User();
        admin.setUsername("admin01");
        admin.setEmail("admin@example.com");
        admin.setPassword("test-password-hash");
        admin.setRole("ROLE_ADMIN");

        entityManager.persistAndFlush(admin);
        Long adminId = admin.getId();

        // 対象IDは履歴として保持し、Contactとの関連は持たない
        AdminOperationLog operationLog = new AdminOperationLog(
                admin,
                "CONTACT",
                12L,
                "STATUS_CHANGE",
                "UNANSWERED",
                "IN_PROGRESS"
        );

        AdminOperationLog saved =
                operationLogRepository.saveAndFlush(operationLog);

        Long logId = saved.getId();
        assertThat(logId).isNotNull();

        // メモリ上のEntityではなく、DBから読み直して確認する
        entityManager.clear();

        AdminOperationLog stored = operationLogRepository
                .findById(logId)
                .orElseThrow();

        assertThat(stored.getAdminUser().getId())
                .isEqualTo(adminId);
        assertThat(stored.getTargetType())
                .isEqualTo("CONTACT");
        assertThat(stored.getTargetId())
                .isEqualTo(12L);
        assertThat(stored.getAction())
                .isEqualTo("STATUS_CHANGE");
        assertThat(stored.getBeforeStatus())
                .isEqualTo("UNANSWERED");
        assertThat(stored.getAfterStatus())
                .isEqualTo("IN_PROGRESS");
        assertThat(stored.getResult())
                .isEqualTo("SUCCESS");
        assertThat(stored.getCreatedAt())
                .isNotNull();
    }
}