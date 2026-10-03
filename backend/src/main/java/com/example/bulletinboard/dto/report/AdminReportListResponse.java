package com.example.bulletinboard.dto.report;

import java.time.LocalDateTime;

import com.example.bulletinboard.model.ReportReason;
import com.example.bulletinboard.model.ReportStatus;
import com.example.bulletinboard.model.ReportTargetType;

/** 管理者用一覧。メールアドレスと証拠本文は詳細で取得する。 */
public record AdminReportListResponse(
        Long id,
        ReportTargetType targetType,
        Long targetId,
        Long reporterUserId,
        String reporterUsername,
        Long targetOwnerUserId,
        String targetOwnerUsername,
        ReportReason reason,
        String reasonDisplayName,
        ReportStatus status,
        LocalDateTime createdAt) {
}
