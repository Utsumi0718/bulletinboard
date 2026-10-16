package com.example.bulletinboard.exception;

import org.springframework.http.HttpStatus;

/** Profile APIの公開文言。原因例外のメッセージや画像参照を公開しない。 */
public class ProfileOperationException extends RuntimeException {
    public enum Reason {
        INVALID_IMAGE(HttpStatus.BAD_REQUEST, "JPEG、PNG、WebP形式の読み取り可能な画像を指定してください。"),
        TOO_LARGE(HttpStatus.BAD_REQUEST, "画像サイズは2MB以下にしてください。"),
        FORBIDDEN(HttpStatus.FORBIDDEN, "この操作は許可されていません。"),
        NOT_FOUND(HttpStatus.NOT_FOUND, "プロフィールまたは画像が見つかりません。"),
        FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "プロフィールの処理を完了できませんでした。");

        public final HttpStatus status;
        public final String message;
        Reason(HttpStatus status, String message) { this.status = status; this.message = message; }
    }

    private final Reason reason;
    public ProfileOperationException(Reason reason) { this(reason, null); }
    public ProfileOperationException(Reason reason, Throwable cause) {
        super(reason.message, cause);
        this.reason = reason;
    }
    public Reason getReason() { return reason; }
}
