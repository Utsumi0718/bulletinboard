package com.example.bulletinboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.bulletinboard.model.AdminOperationLog;
import com.example.bulletinboard.model.Contact;
import com.example.bulletinboard.model.ContactStatus;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.AdminOperationLogRepository;
import com.example.bulletinboard.repository.ContactRepository;
import com.example.bulletinboard.repository.UserRepository;

/**
 * 管理者による状態変更・削除と、操作履歴保存の整合性を検証します。
 *
 * 【検証内容】
 * - 状態変更と履歴のコミット、作成日時の維持、更新日時の更新
 * - 同じ状態では更新日時と履歴件数が変わらないこと
 * - 削除後も履歴が残り、afterStatusがnullであること
 * - 履歴保存失敗時に状態変更・削除がロールバックされること
 *
 * 【テストの構成】
 * - 実際のService・Repository・H2を使用します。
 * - テスト全体を包むトランザクションは無効にします。
 * - データ準備は独立したトランザクションでコミットします。
 * - Service呼び出し後、別のトランザクションでDBを読み直します。
 * - 履歴保存失敗は、一時的なDB制約で再現します。
 *
 * MySQL固有の動作、FlywayのMigration、
 * コミット時の接続断などは、このテストの検証対象外です。
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.ANY
)
@Import(AdminContactService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminContactServiceIntegrationTest {

    private static final String ADMIN_EMAIL =
            "contact-integration-admin@example.com";

    private static final LocalDateTime INITIAL_CREATED_AT =
            LocalDateTime.of(2020, 1, 1, 10, 0);

    private static final LocalDateTime INITIAL_UPDATED_AT =
            LocalDateTime.of(2020, 1, 2, 10, 0);

    @Autowired
    private AdminContactService adminContactService;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminOperationLogRepository operationLogRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TransactionTemplate transactionTemplate;

    private Long adminId;
    private Long contactId;

    @BeforeEach
    void setUp() {

        transactionTemplate =
                new TransactionTemplate(transactionManager);

        // Serviceの処理とは別のトランザクションで準備し、
        // 正常終了時にテストデータをコミットする。
        transactionTemplate.executeWithoutResult(tx -> {

            User admin = new User();
            admin.setUsername("contact-integration-admin");
            admin.setEmail(ADMIN_EMAIL);
            admin.setPassword("test-password-hash");
            admin.setRole("ROLE_ADMIN");

            adminId = userRepository.saveAndFlush(admin).getId();

            Contact contact = new Contact();
            contact.setName("テスト太郎");
            contact.setEmail("contact-test@example.com");
            contact.setSubject("お問い合わせテスト");
            contact.setMessage("統合テスト用のお問い合わせです。");
            contact.setStatus(ContactStatus.UNANSWERED);

            // 過去の日時を設定し、sleepなしで更新を判定する。
            contact.setCreatedAt(INITIAL_CREATED_AT);
            contact.setUpdatedAt(INITIAL_UPDATED_AT);

            contactId =
                    contactRepository.saveAndFlush(contact).getId();
        });
    }

    @AfterEach
    void tearDown() {

        // 途中でテストが失敗しても、一時制約を除去する。
        removeLogFailureConstraint();

        // テスト全体の自動ロールバックを使わないため、
        // このテストで作成したデータを明示的に削除する。
        transactionTemplate.executeWithoutResult(tx -> {

            if (contactId != null) {
                jdbcTemplate.update("""
                        DELETE FROM admin_operation_logs
                        WHERE target_type = ? AND target_id = ?
                        """, "CONTACT", contactId);

                jdbcTemplate.update(
                        "DELETE FROM contacts WHERE id = ?",
                        contactId
                );
            }

            if (adminId != null) {
                jdbcTemplate.update(
                        "DELETE FROM users WHERE id = ?",
                        adminId
                );
            }
        });
    }

    @Test
    @DisplayName("状態変更と履歴がコミットされ作成日時を維持して更新日時が変わる")
    void updateStatus_shouldCommitContactAndLog() {

        Contact result = adminContactService.updateStatus(
                contactId,
                ContactStatus.IN_PROGRESS,
                ADMIN_EMAIL
        );

        // Serviceが戻った後、新しいトランザクションで読み直す。
        transactionTemplate.executeWithoutResult(tx -> {

            Contact stored = contactRepository.findById(contactId)
                    .orElseThrow();

            assertThat(stored.getStatus())
                    .isEqualTo(ContactStatus.IN_PROGRESS);
            assertThat(stored.getCreatedAt())
                    .isEqualTo(INITIAL_CREATED_AT);
            assertThat(stored.getUpdatedAt())
                    .isAfter(INITIAL_UPDATED_AT);

            // DBの日時精度による丸めを考慮し、
            // 戻り値も更新済みであることを別に確認する。
            assertThat(result.getUpdatedAt())
                    .isAfter(INITIAL_UPDATED_AT);

            assertThat(operationLogRepository.findAll())
                    .singleElement()
                    .satisfies(log ->
                            assertOperationLog(
                                    log,
                                    "STATUS_CHANGE",
                                    "IN_PROGRESS"
                            )
                    );
        });
    }

    @Test
    @DisplayName("同じ状態なら更新日時と既存履歴が変わらない")
    void updateStatus_whenSameStatus_shouldKeepTimestampAndLog() {

        // まず実際に状態変更し、既存の成功履歴を作る。
        adminContactService.updateStatus(
                contactId,
                ContactStatus.IN_PROGRESS,
                ADMIN_EMAIL
        );

        LocalDateTime beforeUpdatedAt =
                transactionTemplate.execute(tx ->
                        contactRepository.findById(contactId)
                                .orElseThrow()
                                .getUpdatedAt()
                );

        Long beforeLogId = transactionTemplate.execute(tx -> {
            var logs = operationLogRepository.findAll();
            assertThat(logs).hasSize(1);
            return logs.get(0).getId();
        });

        // 現在と同じ状態を再指定する。
        adminContactService.updateStatus(
                contactId,
                ContactStatus.IN_PROGRESS,
                ADMIN_EMAIL
        );

        transactionTemplate.executeWithoutResult(tx -> {

            Contact stored = contactRepository.findById(contactId)
                    .orElseThrow();

            assertThat(stored.getStatus())
                    .isEqualTo(ContactStatus.IN_PROGRESS);
            assertThat(stored.getCreatedAt())
                    .isEqualTo(INITIAL_CREATED_AT);
            assertThat(stored.getUpdatedAt())
                    .isEqualTo(beforeUpdatedAt);

            assertThat(operationLogRepository.findAll())
                    .singleElement()
                    .satisfies(log ->
                            assertThat(log.getId()).isEqualTo(beforeLogId)
                    );
        });
    }

    @Test
    @DisplayName("削除がコミットされafterStatusがnullの履歴が残る")
    void deleteContact_shouldCommitDeletionAndKeepLog() {

        adminContactService.deleteContact(
                contactId,
                ADMIN_EMAIL
        );

        transactionTemplate.executeWithoutResult(tx -> {

            assertThat(contactRepository.findById(contactId))
                    .isEmpty();

            assertThat(operationLogRepository.findAll())
                    .singleElement()
                    .satisfies(log ->
                            assertOperationLog(log, "DELETE", null)
                    );
        });
    }

    @Test
    @DisplayName("履歴保存失敗時は状態と更新日時が戻り成功履歴が残らない")
    void updateStatus_whenLogSaveFails_shouldRollback() {

        addLogFailureConstraint();

        try {
            assertThatThrownBy(() ->
                    adminContactService.updateStatus(
                            contactId,
                            ContactStatus.IN_PROGRESS,
                            ADMIN_EMAIL
                    )
            ).isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            removeLogFailureConstraint();
        }

        assertOriginalContactAndNoLogs();
    }

    @Test
    @DisplayName("履歴保存失敗時は削除が取り消され成功履歴が残らない")
    void deleteContact_whenLogSaveFails_shouldRollback() {

        addLogFailureConstraint();

        try {
            assertThatThrownBy(() ->
                    adminContactService.deleteContact(
                            contactId,
                            ADMIN_EMAIL
                    )
            ).isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            removeLogFailureConstraint();
        }

        assertOriginalContactAndNoLogs();
    }

    /**
     * ロールバック後に、準備時の状態が残っていることを確認します。
     */
    private void assertOriginalContactAndNoLogs() {

        transactionTemplate.executeWithoutResult(tx -> {

            Contact stored = contactRepository.findById(contactId)
                    .orElseThrow();

            assertThat(stored.getStatus())
                    .isEqualTo(ContactStatus.UNANSWERED);
            assertThat(stored.getCreatedAt())
                    .isEqualTo(INITIAL_CREATED_AT);
            assertThat(stored.getUpdatedAt())
                    .isEqualTo(INITIAL_UPDATED_AT);

            assertThat(operationLogRepository.count()).isZero();
        });
    }

    /**
     * DBから取得した履歴の内容を確認します。
     * 呼び出し元の読み取りトランザクション内で実行します。
     */
    private void assertOperationLog(
            AdminOperationLog log,
            String expectedAction,
            String expectedAfterStatus) {

        assertThat(log.getId()).isNotNull();
        assertThat(log.getAdminUser().getId()).isEqualTo(adminId);
        assertThat(log.getTargetType()).isEqualTo("CONTACT");
        assertThat(log.getTargetId()).isEqualTo(contactId);
        assertThat(log.getAction()).isEqualTo(expectedAction);
        assertThat(log.getBeforeStatus()).isEqualTo("UNANSWERED");
        assertThat(log.getAfterStatus()).isEqualTo(expectedAfterStatus);
        assertThat(log.getResult()).isEqualTo("SUCCESS");
        assertThat(log.getCreatedAt()).isNotNull();
    }

    /**
     * CONTACTの履歴INSERTを失敗させる、テスト専用の制約です。
     * Serviceのトランザクション開始前に追加します。
     */
    private void addLogFailureConstraint() {

        jdbcTemplate.execute("""
                ALTER TABLE admin_operation_logs
                ADD CONSTRAINT ck_test_reject_contact_log
                CHECK (target_type <> 'CONTACT')
                """);
    }

    /**
     * テスト専用の制約を取り除きます。
     * Serviceのトランザクション終了後に実行します。
     */
    private void removeLogFailureConstraint() {

        jdbcTemplate.execute("""
                ALTER TABLE admin_operation_logs
                DROP CONSTRAINT IF EXISTS ck_test_reject_contact_log
                """);
    }
}