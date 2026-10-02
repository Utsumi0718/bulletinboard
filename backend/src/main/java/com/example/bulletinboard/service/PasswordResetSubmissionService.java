package com.example.bulletinboard.service;

import org.springframework.dao.DataAccessException;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

import com.example.bulletinboard.dto.auth.PasswordResetConfirmRequest;
import com.example.bulletinboard.exception.PasswordResetException;

/** DBトランザクション境界の外側で、メール失敗時のトークン取消と安全な例外変換を行う。 */
@Service
public class PasswordResetSubmissionService {
    private final PasswordResetService reset;
    private final PasswordResetNotificationService notification;

    public PasswordResetSubmissionService(PasswordResetService reset,
            PasswordResetNotificationService notification) {
        this.reset = reset;
        this.notification = notification;
    }

    public void request(String email) {
        PasswordResetService.IssuedToken issued;
        try {
            issued = reset.issue(email).orElse(null);
        } catch (DataAccessException | TransactionException ex) {
            throw new PasswordResetException(PasswordResetException.Reason.FAILED, ex);
        }
        if (issued == null) return;

        try {
            notification.send(issued);
        } catch (MailException ex) {
            try {
                reset.revoke(issued.rawToken());
            } catch (DataAccessException | TransactionException ignored) {
                // 公開応答とログへトークンやメールアドレスを出さない。
            }
            throw new PasswordResetException(PasswordResetException.Reason.MAIL_UNAVAILABLE, ex);
        }
    }

    public void confirm(PasswordResetConfirmRequest request) {
        try {
            reset.confirm(request.token(), request.newPassword());
        } catch (PasswordResetException ex) {
            throw ex;
        } catch (DataAccessException | TransactionException ex) {
            throw new PasswordResetException(PasswordResetException.Reason.FAILED, ex);
        }
    }
}
