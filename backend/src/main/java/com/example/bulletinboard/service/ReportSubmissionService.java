package com.example.bulletinboard.service;

import java.sql.SQLException;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.bulletinboard.dto.report.ReportRequest;
import com.example.bulletinboard.exception.ReportOperationException;
import com.example.bulletinboard.exception.ReportOperationException.Reason;
import com.example.bulletinboard.model.ReportTargetType;
import com.example.bulletinboard.repository.ReportRepository;
import com.example.bulletinboard.repository.UserRepository;

/** DBコミット後に受付成功とし、同時重複と障害を区別する境界。 */
@Service
public class ReportSubmissionService {
    private final ReportService reportService;
    private final ReportRepository reports;
    private final UserRepository users;

    public ReportSubmissionService(ReportService reportService, ReportRepository reports, UserRepository users) {
        this.reportService = reportService;
        this.reports = reports;
        this.users = users;
    }

    @Transactional(propagation = Propagation.NEVER)
    public void submitTopic(Long topicId, String email, ReportRequest request) {
        submit(ReportTargetType.TOPIC, topicId, email, () -> reportService.submitTopic(topicId, email, request));
    }

    @Transactional(propagation = Propagation.NEVER)
    public void submitAnswer(Long answerId, String email, ReportRequest request) {
        submit(ReportTargetType.ANSWER, answerId, email, () -> reportService.submitAnswer(answerId, email, request));
    }

    @Transactional(propagation = Propagation.NEVER)
    public void submitProfile(Long profileId, String email, ReportRequest request) {
        submit(ReportTargetType.PROFILE, profileId, email,
                () -> reportService.submitProfile(profileId, email, request));
    }

    private void submit(ReportTargetType targetType, Long targetId, String email, Runnable action) {
        try {
            action.run();
        } catch (DataAccessException | TransactionException ex) {
            // 失敗した保存トランザクションの外から、確定した行だけを確認する。
            if (isUniqueViolation(ex) && users.findByEmail(email)
                    .map(user -> reports.existsByReporterUserIdAndTargetTypeAndTargetId(
                            user.getId(), targetType, targetId))
                    .orElse(false)) {
                throw new ReportOperationException(Reason.DUPLICATE, targetId);
            }
            throw new ReportOperationException(Reason.FAILED, targetId, ex);
        }
    }

    private boolean isUniqueViolation(Throwable cause) {
        for (Throwable cursor = cause; cursor != null; cursor = cursor.getCause()) {
            if (cursor instanceof SQLException sql &&
                    ("23505".equals(sql.getSQLState()) || sql.getErrorCode() == 1062)) {
                return true;
            }
        }
        return false;
    }
}
