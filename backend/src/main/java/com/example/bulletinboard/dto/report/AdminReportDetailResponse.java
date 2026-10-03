package com.example.bulletinboard.dto.report;

import java.time.LocalDateTime;
import java.util.List;

import com.example.bulletinboard.model.ReportReason;
import com.example.bulletinboard.model.ReportStatus;
import com.example.bulletinboard.model.ReportTargetType;

/** 管理者限定の通報詳細。保存証拠と現在の対象状態を分ける。 */
public record AdminReportDetailResponse(
        Long id,
        ReportTargetType targetType,
        Long targetId,
        AdminReportUserResponse reporter,
        Long targetOwnerUserId,
        AdminReportUserResponse targetOwner,
        ReportReason reason,
        String reasonDisplayName,
        String detail,
        ReportStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long reporterReportCount,
        String currentTargetState,
        AdminReportEvidenceResponse evidence,
        List<AdminReportHistoryResponse> history) {
}
