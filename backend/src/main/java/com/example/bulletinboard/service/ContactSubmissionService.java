package com.example.bulletinboard.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.bulletinboard.dto.contact.ContactRequest;
import com.example.bulletinboard.exception.ContactSaveException;
import com.example.bulletinboard.model.Contact;

/**
 * 【クラスの役割】
 * お問い合わせ受付の保存・通知の処理順序を管理します。
 *
 * 【主な処理】
 * - ContactServiceでお問い合わせを保存する
 * - 保存・コミット失敗をContactSaveExceptionへ変換する
 * - 保存がコミットされてから通知メールを送信する
 * - 通知失敗時はログを記録し、受付成功を維持する
 *
 * 保存と通知は別々のtry-catchで扱います。
 */
@Service
public class ContactSubmissionService {

    private static final Logger log =
            LoggerFactory.getLogger(ContactSubmissionService.class);

    private final ContactService contactService;
    private final ContactNotificationService notificationService;

    public ContactSubmissionService(
            ContactService contactService,
            ContactNotificationService notificationService) {

        this.contactService = contactService;
        this.notificationService = notificationService;
    }

    /**
     * お問い合わせを保存し、管理者への通知を試みます。
     *
     * 既存トランザクション内からの呼び出しは拒否します。
     * 保存用トランザクションはContactService側で管理します。
     */
    @Transactional(propagation = Propagation.NEVER)
    public void submitContact(ContactRequest request) {

        Contact savedContact;

        // 1. 保存・コミットの失敗を専用例外へ変換する
        try {
            savedContact = contactService.createContact(request);

        } catch (DataAccessException | TransactionException ex) {

            log.error(
                    "お問い合わせの保存処理に失敗しました。errorType={}",
                    ex.getClass().getSimpleName()
            );

            // 保存失敗時は、ここで終了して通知へ進まない
            throw new ContactSaveException(ex);
        }

        // 2. 保存成功後に通知する。通知失敗でも受付成功を維持する
        try {
            notificationService.sendNotification(savedContact);

        } catch (MailException ex) {

            log.error(
                    "お問い合わせ通知メールの送信に失敗しました。"
                    + " contactId={}, errorType={}",
                    savedContact.getId(),
                    ex.getClass().getSimpleName()
            );
        }
    }
}