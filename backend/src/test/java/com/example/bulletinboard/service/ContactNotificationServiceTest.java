package com.example.bulletinboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import com.example.bulletinboard.model.Contact;

/**
 * 【クラスの役割】
 * ContactNotificationServiceによるお問い合わせ通知メールの
 * 組み立てと、送信処理の呼び出しを検証する単体テストクラスです。
 *
 * 【主な検証内容】
 * - 送信元・宛先が管理者用のアドレスになっていること
 * - 返信先がお問い合わせ者のメールアドレスになっていること
 * - 件名・本文にお問い合わせ情報が正しく反映されること
 * - JavaMailSenderの送信処理が1回呼ばれること
 * - 送信失敗時の例外が呼び出し元へ伝わること
 *
 * 【テストの範囲】
 * JavaMailSenderをモックに置き換えるため、
 * 実際のメール送信やメールサーバーへの接続は行いません。
 *
 * DB保存や、通知失敗時にも保存済みデータが保持されることは、
 * このクラスでは検証しません。
 */

@ExtendWith(MockitoExtension.class)
class ContactNotificationServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private ContactNotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new ContactNotificationService(
                mailSender,
                "admin@example.com"
        );
    }

    @Test
    @DisplayName("保存済みのお問い合わせから管理者向け通知メールを作成して送信する")
    void sendNotification_shouldSendContactDetails() {

        Contact contact = createContact();

        notificationService.sendNotification(contact);

        // メール送信処理へ渡されたオブジェクトを取り出す
        ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(mailSender).send(captor.capture());

        SimpleMailMessage mail = captor.getValue();

        assertThat(mail.getFrom())
                .isEqualTo("admin@example.com");

        assertThat(mail.getTo())
                .containsExactly("admin@example.com");

        assertThat(mail.getReplyTo())
                .isEqualTo("user@example.com");

        assertThat(mail.getSubject())
                .isEqualTo("[お問い合わせ]ログインについて");

        assertThat(mail.getText())
                .isEqualTo(
                        "お問い合わせID：1\n"
                        + "お名前：テスト太郎\n"
                        + "メールアドレス：user@example.com\n\n"
                        + "[お問い合わせ内容]\n"
                        + "ログイン方法を教えてください。"
                );
    }

    @Test
    @DisplayName("メール送信に失敗した場合は例外を呼び出し元へ伝える")
    void sendNotification_whenSendFails_shouldPropagateException() {

        Contact contact = createContact();

        MailSendException failure =
                new MailSendException("テスト用のメール送信失敗");

        doThrow(failure)
                .when(mailSender)
                .send(any(SimpleMailMessage.class));

        assertThatThrownBy(
                () -> notificationService.sendNotification(contact)
        ).isSameAs(failure);

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    /**
     * DB保存済みの状態を想定したテストデータ。
     * 実際のDB保存は行いません。
     */
    private Contact createContact() {

        Contact contact = new Contact();

        contact.setId(1L);
        contact.setName("テスト太郎");
        contact.setEmail("user@example.com");
        contact.setSubject("ログインについて");
        contact.setMessage("ログイン方法を教えてください。");

        return contact;
    }
}
