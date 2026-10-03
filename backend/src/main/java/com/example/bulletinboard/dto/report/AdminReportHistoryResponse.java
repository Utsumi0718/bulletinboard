package com.example.bulletinboard.dto.report;

import java.time.LocalDateTime;

import com.example.bulletinboard.model.AdminOperationLog;

/** 状態変更の成功履歴。 */
public record AdminReportHistoryResponse(
        Long id, Long adminUserId, String action, String beforeStatus,
        String afterStatus, String result, LocalDateTime createdAt) {
    public static AdminReportHistoryResponse from(AdminOperationLog log) {
        return new AdminReportHistoryResponse(log.getId(), log.getAdminUser().getId(), log.getAction(),
                log.getBeforeStatus(), log.getAfterStatus(), log.getResult(), log.getCreatedAt());
    }
}
