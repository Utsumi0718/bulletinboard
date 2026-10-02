package com.example.bulletinboard.exception;

import org.springframework.http.HttpStatus;

public class PasswordResetException extends RuntimeException {
    public enum Reason {
        INVALID(HttpStatus.BAD_REQUEST, "パスワード再設定リンクが無効または期限切れです。"),
        MAIL_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE,
                "パスワード再設定メールを送信できませんでした。時間をおいて再度お試しください。"),
        FAILED(HttpStatus.INTERNAL_SERVER_ERROR,
                "パスワード再設定を完了できませんでした。時間をおいて再度お試しください。");

        public final HttpStatus status;
        public final String message;

        Reason(HttpStatus status, String message) {
            this.status = status;
            this.message = message;
        }
    }

    private final Reason reason;

    public PasswordResetException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public PasswordResetException(Reason reason, Throwable cause) {
        super(reason.name(), cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
