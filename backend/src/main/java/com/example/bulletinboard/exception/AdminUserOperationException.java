package com.example.bulletinboard.exception;

/** 管理用User APIのDB・トランザクション障害。原因の内容は公開しない。 */
public class AdminUserOperationException extends RuntimeException {
    public enum Operation { LIST, DETAIL, TOPICS, ANSWERS, STATUS_CHANGE }

    private final Operation operation;
    private final Long userId;

    public AdminUserOperationException(Operation operation, Long userId, Throwable cause) {
        super("ユーザー管理処理に失敗しました。", cause);
        this.operation = operation;
        this.userId = userId;
    }

    public Operation getOperation() { return operation; }
    public Long getUserId() { return userId; }
}
