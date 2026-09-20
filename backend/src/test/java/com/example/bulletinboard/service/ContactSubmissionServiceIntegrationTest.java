package com.example.bulletinboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;


import org.springframework.mail.MailSendException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.IllegalTransactionStateException;

import com.example.bulletinboard.dto.contact.ContactRequest;
import com.example.bulletinboard.model.Contact;
import com.example.bulletinboard.repository.ContactRepository;
import com.example.bulletinboard.exception.ContactSaveException;
import com.example.bulletinboard.model.ContactStatus;

/**
 * 【クラスの役割】
 * お問い合わせ受付の保存処理と、Springのトランザクション制御を
 * テスト用H2データベースと組み合わせて検証する統合テストです。
 *
 * 【今回の検証内容】
 * - 通知が呼ばれる時点で、保存用トランザクションが終了していること
 * - 別トランザクションから保存済みのお問い合わせを取得できること
 *
 * 【テストの範囲】
 * 受付Service・保存Service・Repositoryは実物を使用します。
 * 通知Serviceだけをモックにし、実際のメール送信は行いません。
 *
 * MySQL固有の動作やFlywayのMigrationは、このテストの対象外です。
 *
 * - DB保存失敗時にデータが増えず、通知されないこと
 * - 既存トランザクション内からの受付呼び出しを拒否すること 
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=never"
})
@Import({
        ContactService.class,
        ContactSubmissionService.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ContactSubmissionServiceIntegrationTest {

    @Autowired
    private ContactSubmissionService submissionService;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private ContactNotificationService notificationService;

    private Long savedContactId;

    @AfterEach
    void cleanUp() {
        // テスト全体をロールバックしないため、作成したデータを削除する
        if (savedContactId != null) {
            contactRepository.deleteById(savedContactId);
        }
    }

    @Test
    @DisplayName("通知時点で保存がコミット済みであり、別トランザクションから取得できる")
    void submitContact_shouldCommitBeforeNotification() {

        ContactRequest request = new ContactRequest();
        request.setName("テスト太郎");
        request.setEmail("user@example.com");
        request.setSubject("ログインについて");
        request.setMessage("ログイン方法を教えてください。");

        // 通知が呼ばれた瞬間に、以下の確認処理を実行する
        doAnswer(invocation -> {

            Contact savedContact = invocation.getArgument(0);
            savedContactId = savedContact.getId();

            assertThat(savedContactId).isNotNull();

            // 保存用トランザクションの中で通知していないことを確認
            assertThat(
                    TransactionSynchronizationManager
                            .isActualTransactionActive()
            ).isFalse();

            // コミット済みデータだけを読む、別トランザクションを用意
            TransactionTemplate readTransaction =
                    new TransactionTemplate(transactionManager);

            readTransaction.setPropagationBehavior(
                    TransactionDefinition.PROPAGATION_REQUIRES_NEW
            );
            readTransaction.setIsolationLevel(
                    TransactionDefinition.ISOLATION_READ_COMMITTED
            );
            readTransaction.setReadOnly(true);

            readTransaction.executeWithoutResult(status -> {

                Contact storedContact = contactRepository
                        .findById(savedContactId)
                        .orElseThrow(() -> new AssertionError(
                                "通知時点でお問い合わせをDBから取得できません"
                        ));

                assertThat(storedContact.getName())
                        .isEqualTo(request.getName());

                assertThat(storedContact.getEmail())
                        .isEqualTo(request.getEmail());

                assertThat(storedContact.getSubject())
                        .isEqualTo(request.getSubject());

                assertThat(storedContact.getMessage())
                        .isEqualTo(request.getMessage());

                assertThat(storedContact.getStatus())
                       .isEqualTo(ContactStatus.UNANSWERED);
            });

            return null;

        }).when(notificationService)
                .sendNotification(any(Contact.class));

        submissionService.submitContact(request);

        verify(notificationService)
                .sendNotification(any(Contact.class));
    }

@Test
@DisplayName("通知に失敗しても受付は正常終了し、お問い合わせがDBに残る")
void submitContact_whenNotificationFails_shouldKeepSavedContact() {

    ContactRequest request = new ContactRequest();
    request.setName("テスト太郎");
    request.setEmail("user@example.com");
    request.setSubject("お問い合わせ通知の確認");
    request.setMessage("通知に失敗しても保存が残ることを確認します。");

    // 通知時に保存済みIDを受け取り、メール送信失敗を再現する
    doAnswer(invocation -> {

        Contact savedContact = invocation.getArgument(0);
        savedContactId = savedContact.getId();

        throw new MailSendException("テスト用のメール送信失敗");

    }).when(notificationService)
            .sendNotification(any(Contact.class));

    // 通知失敗によって受付処理が例外終了しないことを確認
    assertThatCode(
            () -> submissionService.submitContact(request)
    ).doesNotThrowAnyException();

    verify(notificationService)
            .sendNotification(any(Contact.class));

    assertThat(savedContactId).isNotNull();

    // 受付処理終了後、別トランザクションでDBの保存状態を確認する
    TransactionTemplate readTransaction =
            new TransactionTemplate(transactionManager);

    readTransaction.setPropagationBehavior(
            TransactionDefinition.PROPAGATION_REQUIRES_NEW
    );
    readTransaction.setIsolationLevel(
            TransactionDefinition.ISOLATION_READ_COMMITTED
    );
    readTransaction.setReadOnly(true);

    readTransaction.executeWithoutResult(status -> {

        Contact storedContact = contactRepository
                .findById(savedContactId)
                .orElseThrow(() -> new AssertionError(
                        "通知失敗後にお問い合わせがDBに残っていません"
                ));

        assertThat(storedContact.getName())
                .isEqualTo(request.getName());

        assertThat(storedContact.getEmail())
                .isEqualTo(request.getEmail());

        assertThat(storedContact.getSubject())
                .isEqualTo(request.getSubject());

        assertThat(storedContact.getMessage())
                .isEqualTo(request.getMessage());

        assertThat(storedContact.getStatus())
               .isEqualTo(ContactStatus.UNANSWERED);
    });
}

@Test
@DisplayName("DB保存失敗時はデータが増えず、通知も実行されない")
void submitContact_whenDatabaseSaveFails_shouldNotPersistOrNotify() {

    long countBefore = contactRepository.count();

    ContactRequest request = new ContactRequest();

    // contacts.nameのDB上限100文字を超過させる
    request.setName("a".repeat(101));
    request.setEmail("user@example.com");
    request.setSubject("保存失敗の確認");
    request.setMessage("DB保存に失敗した場合の動作を確認します。");

     assertThatThrownBy(
        () -> submissionService.submitContact(request)
        ).isInstanceOf(ContactSaveException.class)
       .hasCauseInstanceOf(DataIntegrityViolationException.class);

    // 保存処理終了後、別トランザクションでDB件数を確認する
    TransactionTemplate readTransaction =
            new TransactionTemplate(transactionManager);

    readTransaction.setPropagationBehavior(
            TransactionDefinition.PROPAGATION_REQUIRES_NEW
    );
    readTransaction.setIsolationLevel(
            TransactionDefinition.ISOLATION_READ_COMMITTED
    );
    readTransaction.setReadOnly(true);

    readTransaction.executeWithoutResult(status -> {
        assertThat(contactRepository.count())
                .isEqualTo(countBefore);
    });

    // 保存できなかったため、通知には進まない
    verifyNoInteractions(notificationService);
}

@Test
@DisplayName("既存トランザクションからの受付呼び出しを拒否する")
void submitContact_withExistingTransaction_shouldReject() {

    long countBefore = contactRepository.count();

    ContactRequest request = new ContactRequest();
    request.setName("テスト太郎");
    request.setEmail("user@example.com");
    request.setSubject("トランザクションの確認");
    request.setMessage("既存トランザクションから呼び出します。");

    TransactionTemplate outerTransaction =
            new TransactionTemplate(transactionManager);

    assertThatThrownBy(() ->
            outerTransaction.executeWithoutResult(status -> {

                // トランザクションを開始した状態であることを確認
                assertThat(
                        TransactionSynchronizationManager
                                .isActualTransactionActive()
                ).isTrue();

                submissionService.submitContact(request);
            })
    ).isInstanceOf(IllegalTransactionStateException.class);

    // 呼び出しが拒否されたため、データは増えない
    assertThat(contactRepository.count())
            .isEqualTo(countBefore);

    verifyNoInteractions(notificationService);
}
}