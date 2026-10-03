package com.example.bulletinboard.service;

import java.util.Arrays;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.bulletinboard.dto.report.ReportRequest;
import com.example.bulletinboard.exception.ReportOperationException;
import com.example.bulletinboard.exception.ReportOperationException.Reason;
import com.example.bulletinboard.model.AccountStatus;
import com.example.bulletinboard.model.Answer;
import com.example.bulletinboard.model.Profile;
import com.example.bulletinboard.model.Report;
import com.example.bulletinboard.model.ReportAnswerSnapshot;
import com.example.bulletinboard.model.ReportProfileSnapshot;
import com.example.bulletinboard.model.ReportReason;
import com.example.bulletinboard.model.ReportTargetType;
import com.example.bulletinboard.model.ReportTopicSnapshot;
import com.example.bulletinboard.model.TopicImage;
import com.example.bulletinboard.model.Topic;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.ReportRepository;
import com.example.bulletinboard.repository.ReportAnswerSnapshotRepository;
import com.example.bulletinboard.repository.ReportProfileSnapshotRepository;
import com.example.bulletinboard.repository.ReportTopicSnapshotRepository;
import com.example.bulletinboard.repository.AnswerRepository;
import com.example.bulletinboard.repository.ProfileRepository;
import com.example.bulletinboard.repository.TopicRepository;
import com.example.bulletinboard.repository.UserRepository;

/** お題・回答・プロフィールの通報判定と、通報情報・証拠の原子的な保存を担当する。 */
@Service
public class ReportService {
    private final UserRepository users;
    private final TopicRepository topics;
    private final AnswerRepository answers;
    private final ProfileRepository profiles;
    private final TopicImageService images;
    private final ReportRepository reports;
    private final ReportTopicSnapshotRepository snapshots;
    private final ReportAnswerSnapshotRepository answerSnapshots;
    private final ReportProfileSnapshotRepository profileSnapshots;

    public ReportService(UserRepository users, TopicRepository topics, AnswerRepository answers,
            ProfileRepository profiles,
            TopicImageService images, ReportRepository reports,
            ReportTopicSnapshotRepository snapshots, ReportAnswerSnapshotRepository answerSnapshots,
            ReportProfileSnapshotRepository profileSnapshots) {
        this.users = users;
        this.topics = topics;
        this.answers = answers;
        this.profiles = profiles;
        this.images = images;
        this.reports = reports;
        this.snapshots = snapshots;
        this.answerSnapshots = answerSnapshots;
        this.profileSnapshots = profileSnapshots;
    }

    @Transactional
    public void submitTopic(Long topicId, String reporterEmail, ReportRequest request) {
        validate(request, topicId);
        User reporter = requireActiveReporter(reporterEmail, topicId);

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

    @Transactional
    public void submitAnswer(Long answerId, String reporterEmail, ReportRequest request) {
        validate(request, answerId);
        User reporter = requireActiveReporter(reporterEmail, answerId);
        // 親Topicを先にロックする。編集・削除側はAnswer行をロックする。
        Long topicId = answers.findReportableTopicIdByAnswerId(answerId)
                .orElseThrow(() -> new ReportOperationException(Reason.NOT_FOUND, answerId));
        Topic topic = topics.findReportableByIdForUpdate(topicId)
                .orElseThrow(() -> new ReportOperationException(Reason.NOT_FOUND, answerId));
        Answer answer = answers.findReportableByIdForUpdate(answerId)
                .orElseThrow(() -> new ReportOperationException(Reason.NOT_FOUND, answerId));
        if (!answer.getTopic().getId().equals(topic.getId())) {
            throw new ReportOperationException(Reason.NOT_FOUND, answerId);
        }
        Long ownerId = answer.getUser().getId();
        if (ownerId.equals(reporter.getId())) {
            throw new ReportOperationException(Reason.FORBIDDEN, answerId);
        }
        if (reports.existsByReporterUserIdAndTargetTypeAndTargetId(
                reporter.getId(), ReportTargetType.ANSWER, answerId)) {
            throw new ReportOperationException(Reason.DUPLICATE, answerId);
        }
        // 親お題の画像原本も保持できる場合だけ受付成功にする。
        TopicImage image = images.findOwnedSource(topic.getImage(), topic.getUser().getId())
                .orElseThrow(() -> new ReportOperationException(Reason.FAILED, answerId));
        Report report = new Report();
        report.setReporterUser(reporter);
        report.setTargetType(ReportTargetType.ANSWER);
        report.setTargetId(answerId);
        report.setTargetOwnerUserId(ownerId);
        report.setReason(request.reason());
        report.setDetail(request.detail());
        report = reports.saveAndFlush(report);
        answerSnapshots.saveAndFlush(new ReportAnswerSnapshot(report, answer, topic, image));
    }

    @Transactional
    public void submitProfile(Long profileId, String reporterEmail, ReportRequest request) {
        validate(request, profileId);
        User reporter = requireActiveReporter(reporterEmail, profileId);
        Profile profile = profiles.findByIdForReport(profileId)
                .orElseThrow(() -> new ReportOperationException(Reason.NOT_FOUND, profileId));
        User owner = profile.getUser();
        if (owner.getAccountStatus() == AccountStatus.WITHDRAWN) {
            throw new ReportOperationException(Reason.NOT_FOUND, profileId);
        }
        if (owner.getId().equals(reporter.getId())) {
            throw new ReportOperationException(Reason.FORBIDDEN, profileId);
        }
        if (reports.existsByReporterUserIdAndTargetTypeAndTargetId(
                reporter.getId(), ReportTargetType.PROFILE, profileId)) {
            throw new ReportOperationException(Reason.DUPLICATE, profileId);
        }
        // 現在のProfile.iconはURL/パスのみで原本を取得できない。証拠を欠いた受付を防ぐ。
        if (profile.getIcon() != null) {
            throw new ReportOperationException(Reason.FAILED, profileId);
        }
        Report report = new Report();
        report.setReporterUser(reporter);
        report.setTargetType(ReportTargetType.PROFILE);
        report.setTargetId(profileId);
        report.setTargetOwnerUserId(owner.getId());
        report.setReason(request.reason());
        report.setDetail(request.detail());
        report = reports.saveAndFlush(report);
        profileSnapshots.saveAndFlush(new ReportProfileSnapshot(report, profile));
    }

    /** 保持内容は管理者だけが参照する。画像取得時にもDB上のACTIVEを再確認する。 */
    @Transactional(readOnly = true)
    public EvidenceImage getEvidenceImage(Long reportId, String adminEmail) {
        User admin = users.findByEmail(adminEmail)
                .orElseThrow(() -> new ReportOperationException(Reason.FORBIDDEN, reportId));
        if (admin.getAccountStatus() != AccountStatus.ACTIVE || !"ROLE_ADMIN".equals(admin.getRole())) {
            throw new ReportOperationException(Reason.FORBIDDEN, reportId);
        }
        Report report = reports.findById(reportId)
                .orElseThrow(() -> new ReportOperationException(Reason.NOT_FOUND, reportId));
        if (report.getTargetType() == ReportTargetType.ANSWER) {
            ReportAnswerSnapshot snapshot = answerSnapshots.findById(reportId)
                    .orElseThrow(() -> new ReportOperationException(Reason.NOT_FOUND, reportId));
            return new EvidenceImage(snapshot.getImageContent(), snapshot.getContentType());
        }
        if (report.getTargetType() != ReportTargetType.TOPIC) {
            throw new ReportOperationException(Reason.NOT_FOUND, reportId);
        }
        ReportTopicSnapshot snapshot = snapshots.findById(reportId)
                .orElseThrow(() -> new ReportOperationException(Reason.NOT_FOUND, reportId));
        return new EvidenceImage(snapshot.getImageContent(), snapshot.getContentType());
    }

    private User requireActiveReporter(String email, Long targetId) {
        User reporter = users.findByEmail(email)
                .orElseThrow(() -> new ReportOperationException(Reason.FORBIDDEN, targetId));
        if (reporter.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new ReportOperationException(Reason.FORBIDDEN, targetId);
        }
        return reporter;
    }

    private void validate(ReportRequest request, Long targetId) {
        if (request == null || request.reason() == null ||
                (request.detail() != null && request.detail().length() > 500) ||
                (request.reason() == ReportReason.OTHER &&
                        (request.detail() == null || request.detail().isBlank()))) {
            throw new ReportOperationException(Reason.INVALID, targetId);
        }
    }

    public record EvidenceImage(byte[] bytes, String contentType) {
        public EvidenceImage { bytes = Arrays.copyOf(bytes, bytes.length); }
        @Override public byte[] bytes() { return Arrays.copyOf(bytes, bytes.length); }
    }
}
