package com.example.bulletinboard.exception;

import org.springframework.http.HttpStatus;

public class WithdrawalException extends RuntimeException {
    public enum Reason {
        PASSWORD_MISMATCH(HttpStatus.FORBIDDEN, "現在のパスワードが正しくありません。"),
        FORBIDDEN(HttpStatus.FORBIDDEN, "このアカウントでは退会処理を実行できません。"),
        FAILED(HttpStatus.INTERNAL_SERVER_ERROR,
                "退会処理を完了できませんでした。時間をおいて再度お試しください。");

        public final HttpStatus status;
        public final String message;

        Reason(HttpStatus status, String message) {
            this.status = status;
            this.message = message;
        }
    }

    private final Reason reason;

    public WithdrawalException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public WithdrawalException(Reason reason, Throwable cause) {
        super(reason.name(), cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
