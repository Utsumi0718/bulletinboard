package com.example.bulletinboard.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.example.bulletinboard.model.Contact;

/**
 * 【クラスの役割】
 * お問い合わせの通知メールを組み立て、管理者へ送信するService。
 *
 * 保存済みのContactを受け取って使用します。
 * DB保存やトランザクションの管理は担当しません。
 */
@Service
public class ContactNotificationService {

    private final JavaMailSender mailSender;
    private final String notificationAddress;

    public ContactNotificationService(
            JavaMailSender mailSender,
            @Value("${spring.mail.username}") String notificationAddress) {

        this.mailSender = mailSender;
        this.notificationAddress = notificationAddress;
    }

    /**
     * 保存済みのお問い合わせについて、管理者へ通知します。
     *
     * メール送信に失敗した場合の例外は呼び出し元へ伝えます。
     * 受付結果とログの扱いは、後で作成する呼び出し元で制御します。
     */
    public void sendNotification(Contact contact) {

        SimpleMailMessage mail = new SimpleMailMessage();

        // 既存処理と同じく、設定されたアドレスを送信元・宛先に使用
        mail.setFrom(notificationAddress);
        mail.setTo(notificationAddress);

        // 管理者が返信した際の返信先
        mail.setReplyTo(contact.getEmail());

        mail.setSubject("[お問い合わせ]" + contact.getSubject());

        mail.setText(
                "お問い合わせID：" + contact.getId() + "\n"
                + "お名前：" + contact.getName() + "\n"
                + "メールアドレス：" + contact.getEmail() + "\n\n"
                + "[お問い合わせ内容]\n"
                + contact.getMessage()
        );

        mailSender.send(mail);
    }
}