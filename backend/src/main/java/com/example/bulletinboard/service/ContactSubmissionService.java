package com.example.bulletinboard.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.bulletinboard.dto.contact.ContactRequest;
import com.example.bulletinboard.model.Contact;

/**
 * 【クラスの役割】
 * お問い合わせ受付全体の処理順序を管理するServiceです。
 *
 * 【処理の流れ】
 * 1. ContactServiceでお問い合わせを保存する
 * 2. DBへのコミットが成功してから通知メールを送信する
 * 3. 通知失敗時はログを記録し、受付成功を維持する
 *
 * 【設計上のポイント】
 * - このメソッド全体をDBトランザクションで囲みません。
 * - 保存処理は別BeanのContactServiceへ委譲します。
 * - 保存失敗は呼び出し元へ伝え、通知処理には進みません。
 * - メール送信失敗のみを捕捉します。
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
     * 戻り値なしで正常終了した場合は、受付成功として扱います。
     * HTTPステータスやレスポンスはController側で組み立てます。
     */
    @Transactional(propagation = Propagation.NEVER)
    public void submitContact(ContactRequest request) {

        // 保存が失敗した場合は例外が伝わり、ここで処理が終了します。
        // ContactServiceのトランザクションがコミットされてから戻ります。
        Contact savedContact = contactService.createContact(request);

        try {
            notificationService.sendNotification(savedContact);

        } catch (MailException ex) {

            // メール本文や認証情報を含む可能性があるため、
            // 例外メッセージや例外オブジェクトをそのまま出力しません。
            log.error(
                    "お問い合わせ通知メールの送信に失敗しました。"
                    + " contactId={}, errorType={}",
                    savedContact.getId(),
                    ex.getClass().getSimpleName()
            );
        }
    }
}