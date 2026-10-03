package com.example.bulletinboard.exception;

/**
 * お問い合わせの管理操作で発生したDB・トランザクション障害を表します。
 *
 * 受付専用のContactSaveExceptionとは区別します。
 * 原因例外は保持しますが、そのメッセージを応答やログへ出しません。
 * 一覧取得（LIST）は単一のお問い合わせを対象にしないため、contactIdはnullです。
 */
public class AdminContactOperationException extends RuntimeException {

    public enum Operation {
        LIST,
        DETAIL,
        STATUS_CHANGE,
        DELETE
    }

    private final Operation operation;
    private final Long contactId;

    public AdminContactOperationException(
            Operation operation,
            Long contactId,
            Throwable cause) {

        super("お問い合わせの管理操作でエラーが発生しました。", cause);
        this.operation = operation;
        this.contactId = contactId;
    }

    public Operation getOperation() {
        return operation;
    }

    public Long getContactId() {
        return contactId;
    }
}
