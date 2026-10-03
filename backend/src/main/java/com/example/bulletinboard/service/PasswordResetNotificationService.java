package com.example.bulletinboard.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** 再設定用の生トークンをリンクに含め、対象ユーザーだけへメール送信する。 */
@Service
public class PasswordResetNotificationService {
    private final JavaMailSender mailSender;
    private final String sender;
    private final String resetUrl;

    public PasswordResetNotificationService(JavaMailSender mailSender,
            @Value("${spring.mail.username}") String sender,
            @Value("${app.password-reset.url:http://localhost:3000/reset-password}") String resetUrl) {
        this.mailSender = mailSender;
        this.sender = sender;
        this.resetUrl = resetUrl;
    }

    public void send(PasswordResetService.IssuedToken token) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(sender);
        mail.setTo(token.email());
        mail.setSubject("パスワード再設定のご案内");
        mail.setText("次のリンクから30分以内にパスワードを再設定してください。\n"
                + resetUrl + "?token=" + token.rawToken());
        mailSender.send(mail);
    }
}
