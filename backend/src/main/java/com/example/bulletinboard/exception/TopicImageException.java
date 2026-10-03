package com.example.bulletinboard.exception;

import org.springframework.http.HttpStatus;

/** 画像処理の公開メッセージを固定し、入力値や原因例外を応答に含めない。 */
public class TopicImageException extends RuntimeException {
    public enum Reason {
        INVALID(HttpStatus.BAD_REQUEST, "JPEG・PNG・WebPの画像を指定してください。"),
        INVALID_REFERENCE(HttpStatus.BAD_REQUEST, "自分でアップロードした画像を指定してください。"),
        TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "画像は5MB以内にしてください。"),
        FORBIDDEN(HttpStatus.FORBIDDEN, "この操作は利用できません。"),
        NOT_FOUND(HttpStatus.NOT_FOUND, "画像が見つかりません。"),
        FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "画像の処理に失敗しました。時間をおいて再度お試しください。");

        public final HttpStatus status;
        public final String message;

        Reason(HttpStatus status, String message) {
            this.status = status;
            this.message = message;
        }
    }

    private final Reason reason;

    public TopicImageException(Reason reason) {
        this(reason, null);
    }

    public TopicImageException(Reason reason, Throwable cause) {
        super(reason.message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
