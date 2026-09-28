package com.example.bulletinboard.service;

import java.util.Arrays;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.bulletinboard.dto.report.TopicReportRequest;
import com.example.bulletinboard.exception.ReportOperationException;
import com.example.bulletinboard.exception.ReportOperationException.Reason;
import com.example.bulletinboard.model.AccountStatus;
import com.example.bulletinboard.model.Report;
import com.example.bulletinboard.model.ReportReason;
import com.example.bulletinboard.model.ReportTargetType;
import com.example.bulletinboard.model.ReportTopicSnapshot;
import com.example.bulletinboard.model.TopicImage;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.ReportRepository;
import com.example.bulletinboard.repository.ReportTopicSnapshotRepository;
import com.example.bulletinboard.repository.TopicRepository;
import com.example.bulletinboard.repository.UserRepository;

/** お題通報の判定、通報情報と証拠の原子的な保存を担当する。 */
@Service
public class ReportService {
    private final UserRepository users;
    private final TopicRepository topics;
    private final TopicImageService images;
    private final ReportRepository reports;
    private final ReportTopicSnapshotRepository snapshots;

    public ReportService(UserRepository users, TopicRepository topics, TopicImageService images,
            ReportRepository reports, ReportTopicSnapshotRepository snapshots) {
        this.users = users;
        this.topics = topics;
        this.images = images;
        this.reports = reports;
        this.snapshots = snapshots;
    }

    @Transactional
    public void submitTopic(Long topicId, String reporterEmail, TopicReportRequest request) {
        validate(request, topicId);
        User reporter = users.findByEmail(reporterEmail)
                .orElseThrow(() -> new ReportOperationException(Reason.FORBIDDEN, topicId));
        if (reporter.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new ReportOperationException(Reason.FORBIDDEN, topicId);
        }

        // 編集・削除と同じ行ロックを取り、文章と画像IDを同じ版から読み取る。
        var topic = topics.findReportableByIdForUpdate(topicId)
                .orElseThrow(() -> new ReportOperationException(Reason.NOT_FOUND, topicId));
        Long ownerId = topic.getUser().getId();
        if (ownerId.equals(reporter.getId())) {
            throw new ReportOperationException(Reason.FORBIDDEN, topicId);
        }
        if (reports.existsByReporterUserIdAndTargetTypeAndTargetId(
                reporter.getId(), ReportTargetType.TOPIC, topicId)) {
            throw new ReportOperationException(Reason.DUPLICATE, topicId);
        }

        // 旧来のパス・URLだけでは元画像の保持を保証できないため受付成功にしない。
        TopicImage image = images.findOwnedSource(topic.getImage(), ownerId)
                .orElseThrow(() -> new ReportOperationException(Reason.FAILED, topicId));
        Report report = new Report();
        report.setReporterUser(reporter);
        report.setTargetType(ReportTargetType.TOPIC);
        report.setTargetId(topicId);
        report.setTargetOwnerUserId(ownerId);
        report.setReason(request.reason());
        report.setDetail(request.detail());
        report = reports.saveAndFlush(report);
        snapshots.saveAndFlush(new ReportTopicSnapshot(report, topic, image));
    }

    /** 保持内容は管理者だけが参照する。画像取得時にもDB上のACTIVEを再確認する。 */
    @Transactional(readOnly = true)
    public EvidenceImage getTopicEvidenceImage(Long reportId, String adminEmail) {
        User admin = users.findByEmail(adminEmail)
                .orElseThrow(() -> new ReportOperationException(Reason.FORBIDDEN, reportId));
        if (admin.getAccountStatus() != AccountStatus.ACTIVE || !"ROLE_ADMIN".equals(admin.getRole())) {
            throw new ReportOperationException(Reason.FORBIDDEN, reportId);
        }
        Report report = reports.findById(reportId)
                .orElseThrow(() -> new ReportOperationException(Reason.NOT_FOUND, reportId));
        if (report.getTargetType() != ReportTargetType.TOPIC) {
            throw new ReportOperationException(Reason.NOT_FOUND, reportId);
        }
        ReportTopicSnapshot snapshot = snapshots.findById(reportId)
                .orElseThrow(() -> new ReportOperationException(Reason.NOT_FOUND, reportId));
        return new EvidenceImage(snapshot.getImageContent(), snapshot.getContentType());
    }

    private void validate(TopicReportRequest request, Long topicId) {
        if (request == null || request.reason() == null ||
                (request.detail() != null && request.detail().length() > 500) ||
                (request.reason() == ReportReason.OTHER &&
                        (request.detail() == null || request.detail().isBlank()))) {
            throw new ReportOperationException(Reason.INVALID, topicId);
        }
    }

    public record EvidenceImage(byte[] bytes, String contentType) {
        public EvidenceImage { bytes = Arrays.copyOf(bytes, bytes.length); }
        @Override public byte[] bytes() { return Arrays.copyOf(bytes, bytes.length); }
    }
}
