package com.example.bulletinboard.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.bulletinboard.dto.report.AdminReportDetailResponse;
import com.example.bulletinboard.dto.report.AdminReportEvidenceResponse;
import com.example.bulletinboard.dto.report.AdminReportHistoryResponse;
import com.example.bulletinboard.dto.report.AdminReportListResponse;
import com.example.bulletinboard.dto.report.AdminReportUserResponse;
import com.example.bulletinboard.exception.ForbiddenOperationException;
import com.example.bulletinboard.exception.ReportOperationException;
import com.example.bulletinboard.exception.UserNotFoundException;
import com.example.bulletinboard.model.AccountStatus;
import com.example.bulletinboard.model.AdminOperationLog;
import com.example.bulletinboard.model.Report;
import com.example.bulletinboard.model.ReportStatus;
import com.example.bulletinboard.model.ReportTargetType;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.AdminOperationLogRepository;
import com.example.bulletinboard.repository.AnswerRepository;
import com.example.bulletinboard.repository.ProfileRepository;
import com.example.bulletinboard.repository.ReportAnswerSnapshotRepository;
import com.example.bulletinboard.repository.ReportProfileSnapshotRepository;
import com.example.bulletinboard.repository.ReportRepository;
import com.example.bulletinboard.repository.ReportTopicSnapshotRepository;
import com.example.bulletinboard.repository.TopicRepository;
import com.example.bulletinboard.repository.UserRepository;

/** 管理者用の通報検索・証拠確認・状態変更を担当する。 */
@Service
public class AdminReportService {
    private static final int MAX_PAGE_SIZE = 100;
    private final ReportRepository reports;
    private final UserRepository users;
    private final TopicRepository topics;
    private final AnswerRepository answers;
    private final ProfileRepository profiles;
    private final ReportTopicSnapshotRepository topicSnapshots;
    private final ReportAnswerSnapshotRepository answerSnapshots;
    private final ReportProfileSnapshotRepository profileSnapshots;
    private final AdminOperationLogRepository operationLogs;

    public AdminReportService(ReportRepository reports, UserRepository users, TopicRepository topics,
            AnswerRepository answers, ProfileRepository profiles,
            ReportTopicSnapshotRepository topicSnapshots, ReportAnswerSnapshotRepository answerSnapshots,
            ReportProfileSnapshotRepository profileSnapshots, AdminOperationLogRepository operationLogs) {
        this.reports = reports;
        this.users = users;
        this.topics = topics;
        this.answers = answers;
        this.profiles = profiles;
        this.topicSnapshots = topicSnapshots;
        this.answerSnapshots = answerSnapshots;
        this.profileSnapshots = profileSnapshots;
        this.operationLogs = operationLogs;
    }

    @Transactional(readOnly = true)
    public Page<AdminReportListResponse> list(int page, int size, ReportStatus status,
            Long reporterUserId, String adminEmail) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE ||
                (reporterUserId != null && reporterUserId < 1)) {
            throw new IllegalArgumentException("一覧条件を確認してください。");
        }
        requireAdmin(adminEmail);
        var pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<Report> results;
        if (status != null && reporterUserId != null) {
            results = reports.findByStatusAndReporterUserId(status, reporterUserId, pageable);
        } else if (status != null) {
            results = reports.findByStatus(status, pageable);
        } else if (reporterUserId != null) {
            results = reports.findByReporterUserId(reporterUserId, pageable);
        } else {
            results = reports.findAll(pageable);
        }
        // 対象所有者は一ページ分をまとめて取得する。古い行のNULLも許容する。
        Map<Long, User> owners = new HashMap<>();
        users.findAllById(results.stream().map(Report::getTargetOwnerUserId)
                .filter(Objects::nonNull).distinct().toList())
                .forEach(owner -> owners.put(owner.getId(), owner));
        return results.map(report -> {
            User owner = owners.get(report.getTargetOwnerUserId());
            return new AdminReportListResponse(report.getId(), report.getTargetType(), report.getTargetId(),
                    report.getReporterUser().getId(), report.getReporterUser().getUsername(),
                    report.getTargetOwnerUserId(), owner == null ? null : owner.getUsername(),
                    report.getReason(), report.getReason().getDisplayName(),
                    report.getStatus(), report.getCreatedAt());
        });
    }

    @Transactional(readOnly = true)
    public AdminReportDetailResponse detail(Long reportId, String adminEmail) {
        requireAdmin(adminEmail);
        Report report = reports.findById(reportId)
                .orElseThrow(() -> new ReportOperationException(ReportOperationException.Reason.NOT_FOUND, reportId));
        return toDetail(report);
    }

    @Transactional
    public AdminReportDetailResponse updateStatus(Long reportId, ReportStatus next, String adminEmail) {
        if (next == null) {
            throw new IllegalArgumentException("ステータスを指定してください。");
        }
        User admin = requireAdmin(adminEmail);
        Report report = reports.findByIdForUpdate(reportId)
                .orElseThrow(() -> new ReportOperationException(ReportOperationException.Reason.NOT_FOUND, reportId));
        ReportStatus before = report.getStatus();
        if (before != next) {
            report.setStatus(next);
            reports.saveAndFlush(report);
            operationLogs.saveAndFlush(new AdminOperationLog(admin, "REPORT", reportId,
                    "STATUS_CHANGE", before.name(), next.name()));
        }
        return toDetail(report);
    }

    private User requireAdmin(String email) {
        if (email == null || email.isBlank()) {
            throw new UserNotFoundException("ログインユーザー情報を取得できませんでした。");
        }
        User admin = users.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("ログインユーザー情報を取得できませんでした。"));
        if (!"ROLE_ADMIN".equals(admin.getRole()) || admin.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new ForbiddenOperationException("この操作は管理者のみ実行できます。");
        }
        return admin;
    }

    private AdminReportDetailResponse toDetail(Report report) {
        User owner = report.getTargetOwnerUserId() == null ? null :
                users.findById(report.getTargetOwnerUserId()).orElse(null);
        return new AdminReportDetailResponse(report.getId(), report.getTargetType(), report.getTargetId(),
                AdminReportUserResponse.from(report.getReporterUser()), report.getTargetOwnerUserId(),
                owner == null ? null : AdminReportUserResponse.from(owner), report.getReason(),
                report.getReason().getDisplayName(), report.getDetail(), report.getStatus(),
                report.getCreatedAt(), report.getUpdatedAt(),
                reports.countByReporterUserId(report.getReporterUser().getId()), currentTargetState(report),
                evidence(report), operationLogs.findByTargetTypeAndTargetIdOrderByCreatedAtAscIdAsc(
                        "REPORT", report.getId()).stream().map(AdminReportHistoryResponse::from).toList());
    }

    private String currentTargetState(Report report) {
        Long id = report.getTargetId();
        return switch (report.getTargetType()) {
            case TOPIC -> topics.findById(id).map(topic -> topic.getDeletedAt() == null ? "PUBLIC" : "DELETED")
                    .orElse("MISSING");
            case ANSWER -> answers.findById(id).map(answer -> {
                if (answer.getDeletedAt() != null || answer.getTopic().getDeletedAt() != null) return "DELETED";
                return "PUBLIC";
            }).orElse("MISSING");
            case PROFILE -> profiles.findById(id).map(profile ->
                    profile.getUser().getAccountStatus() == AccountStatus.WITHDRAWN ? "DELETED" : "PUBLIC")
                    .orElse("MISSING");
        };
    }

    private AdminReportEvidenceResponse evidence(Report report) {
        Long id = report.getId();
        String imageUrl = "/api/admin/reports/" + id + "/evidence-image";
        if (report.getTargetType() == ReportTargetType.TOPIC) {
            return topicSnapshots.findById(id).map(s -> new AdminReportEvidenceResponse(
                    s.getTitle(), s.getQuestion(), null, null, null, null, null, null,
                    true, imageUrl)).orElse(null);
        }
        if (report.getTargetType() == ReportTargetType.ANSWER) {
            return answerSnapshots.findById(id).map(s -> new AdminReportEvidenceResponse(
                    null, null, s.getAnswerContent(), s.getTopicId(), s.getTopicTitle(),
                    s.getTopicQuestion(), null, null, true, imageUrl)).orElse(null);
        }
        return profileSnapshots.findById(id).map(s -> new AdminReportEvidenceResponse(
                null, null, null, null, null, null, s.getDisplayName(), s.getBio(),
                false, null)).orElse(null);
    }
}
