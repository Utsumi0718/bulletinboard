package com.example.bulletinboard.exception;

/** 管理コンテンツAPIのDB障害。原因の内容は公開しない。 */
public class AdminContentOperationException extends RuntimeException {
    public enum Operation { LIST, DETAIL, DELETE, IMAGE }

    private final Operation operation;
    private final String targetType;
    private final Long targetId;

    public AdminContentOperationException(Operation operation, String targetType, Long targetId, Throwable cause) {
        super("コンテンツ管理処理に失敗しました。", cause);
        this.operation = operation;
        this.targetType = targetType;
        this.targetId = targetId;
    }

    public Operation getOperation() { return operation; }
    public String getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
}
