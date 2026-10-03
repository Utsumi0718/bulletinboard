package com.example.bulletinboard.exception;

/** 管理用通報APIのDB・トランザクション障害。原因の内容は公開しない。 */
public class AdminReportOperationException extends RuntimeException {
    public enum Operation { LIST, DETAIL, STATUS_CHANGE }

    private final Operation operation;
    private final Long reportId;

    public AdminReportOperationException(Operation operation, Long reportId, Throwable cause) {
        super("通報管理処理に失敗しました。", cause);
        this.operation = operation;
        this.reportId = reportId;
    }

    public Operation getOperation() { return operation; }
    public Long getReportId() { return reportId; }
}
