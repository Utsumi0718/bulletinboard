package com.example.bulletinboard.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mail.MailSendException;

import com.example.bulletinboard.dto.contact.ContactRequest;
import com.example.bulletinboard.model.Contact;
import com.example.bulletinboard.exception.ContactSaveException;
import com.example.bulletinboard.model.ContactStatus;
/**
 * 【クラスの役割】
 * ContactSubmissionServiceによるお問い合わせ受付の
 * 処理順序と、保存・通知失敗時の動作を検証する単体テストです。
 *
 * 【主な検証内容】
 * - 保存処理の後に、保存結果を使って通知すること
 * - 保存失敗時は例外を呼び出し元へ伝え、通知しないこと
 * - 通知失敗時も受付処理が正常終了すること
 *
 * 【テストの範囲】
 * 保存Serviceと通知Serviceをモックに置き換えるため、
 * 実際のDB保存やメール送信は行いません。
 *
 * Springのトランザクション制御、コミットのタイミング、
 * 通知失敗時のDB保存保持は、別の統合テストで確認します。
 */
@ExtendWith(MockitoExtension.class)
class ContactSubmissionServiceTest {

    @Mock
    private ContactService contactService;

    @Mock
    private ContactNotificationService notificationService;

    @InjectMocks
    private ContactSubmissionService submissionService;

    @Test
    @DisplayName("お問い合わせの保存後に、保存結果を使って通知する")
    void submitContact_shouldSaveThenNotify() {

        ContactRequest request = createRequest();
        Contact savedContact = createSavedContact(request);

        when(contactService.createContact(request))
                .thenReturn(savedContact);

        submissionService.submitContact(request);

        // 2つのServiceが呼ばれた順番を確認する
        InOrder order = inOrder(
                contactService,
                notificationService
        );

        order.verify(contactService).createContact(request);
        order.verify(notificationService).sendNotification(savedContact);
        order.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName("保存失敗時は例外を伝え、通知を実行しない")
    void submitContact_whenSaveFails_shouldNotNotify() {

        ContactRequest request = createRequest();

        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException(
                        "テスト用のDB接続失敗"
                );

        when(contactService.createContact(request))
                .thenThrow(failure);

        assertThatThrownBy(
          () -> submissionService.submitContact(request)
           ).isInstanceOf(ContactSaveException.class)
            .hasCause(failure);

        verify(contactService).createContact(request);

        // 通知Serviceが一度も呼ばれていないことを確認する
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("通知失敗時も受付処理は正常終了する")
    void submitContact_whenNotificationFails_shouldCompleteNormally() {

        ContactRequest request = createRequest();
        Contact savedContact = createSavedContact(request);

        when(contactService.createContact(request))
                .thenReturn(savedContact);

        doThrow(new MailSendException("テスト用のメール送信失敗"))
                .when(notificationService)
                .sendNotification(savedContact);

        // 通知の例外が呼び出し元へ漏れないことを確認する
        assertThatCode(
                () -> submissionService.submitContact(request)
        ).doesNotThrowAnyException();

        verify(contactService).createContact(request);
        verify(notificationService).sendNotification(savedContact);
    }

    private ContactRequest createRequest() {

        ContactRequest request = new ContactRequest();

        request.setName("テスト太郎");
        request.setEmail("user@example.com");
        request.setSubject("ログインについて");
        request.setMessage("ログイン方法を教えてください。");

        return request;
    }

    /**
     * 保存Serviceから返されるContactを想定したテストデータ。
     * 実際のDB保存は行いません。
     */
    private Contact createSavedContact(ContactRequest request) {

        Contact contact = new Contact();

        contact.setId(1L);
        contact.setName(request.getName());
        contact.setEmail(request.getEmail());
        contact.setSubject(request.getSubject());
        contact.setMessage(request.getMessage());
        contact.setStatus(ContactStatus.UNANSWERED);

        return contact;
    }
}