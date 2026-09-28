package com.example.bulletinboard.exception;

import org.springframework.http.HttpStatus;

/** 公開文言を固定した通報専用例外。内部原因はレスポンスとログへ出さない。 */
public class ReportOperationException extends RuntimeException {
    public enum Reason {
        INVALID(HttpStatus.BAD_REQUEST, "通報の入力内容を確認してください。"),
        FORBIDDEN(HttpStatus.FORBIDDEN, "この通報は受け付けられません。"),
        NOT_FOUND(HttpStatus.NOT_FOUND, "通報対象が見つかりません。"),
        DUPLICATE(HttpStatus.CONFLICT, "この対象はすでに通報されています。"),
        FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "通報を受け付けられませんでした。時間をおいて再度お試しください。");

        public final HttpStatus status;
        public final String message;
        Reason(HttpStatus status, String message) {
            this.status = status;
            this.message = message;
        }
    }

    private final Reason reason;
    private final Long targetId;

    public ReportOperationException(Reason reason, Long targetId) { this(reason, targetId, null); }

    public ReportOperationException(Reason reason, Long targetId, Throwable cause) {
        super(reason.message, cause);
        this.reason = reason;
        this.targetId = targetId;
    }

    public Reason getReason() { return reason; }
    public Long getTargetId() { return targetId; }
}
