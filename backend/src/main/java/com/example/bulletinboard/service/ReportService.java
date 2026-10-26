package com.example.bulletinboard.service;
import com.example.bulletinboard.security.ActorIds;

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
@org.springframework.context.annotation.Import(PrivacyModule.class)
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
    private final ProfileImageService profileImages;
    private final PrivacyRetentionService retention;
    private final PrivacyAccessService privacyAccess;

    public ReportService(UserRepository users, TopicRepository topics, AnswerRepository answers,
            ProfileRepository profiles,
            TopicImageService images, ReportRepository reports,
            ReportTopicSnapshotRepository snapshots, ReportAnswerSnapshotRepository answerSnapshots,
            ReportProfileSnapshotRepository profileSnapshots, ProfileImageService profileImages,
            PrivacyRetentionService retention,PrivacyAccessService privacyAccess) {
        this.users = users;
        this.topics = topics;
        this.answers = answers;
        this.profiles = profiles;
        this.images = images;
        this.reports = reports;
        this.snapshots = snapshots;
        this.answerSnapshots = answerSnapshots;
        this.profileSnapshots = profileSnapshots;
        this.profileImages = profileImages;
        this.retention=retention;this.privacyAccess=privacyAccess;
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void submitTopic(Long topicId, String actorId, ReportRequest request) {
        validate(request, topicId);
        User reporter = requireActiveReporter(actorId, topicId);

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
        retention.pending(com.example.bulletinboard.model.PrivacyRetentionRecord.Target.REPORT_EVIDENCE,
                report.getId().toString(),ownerId,report.getId());
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void submitAnswer(Long answerId, String actorId, ReportRequest request) {
        validate(request, answerId);
        User reporter = requireActiveReporter(actorId, answerId);
        // 親Topicを先にロックし、回答の削除処理と同じ順序でAnswer行をロックする。
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
        retention.pending(com.example.bulletinboard.model.PrivacyRetentionRecord.Target.REPORT_EVIDENCE,
                report.getId().toString(),ownerId,report.getId());
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void submitProfile(Long profileId, String actorId, ReportRequest request) {
        validate(request, profileId);
        User reporter = requireActiveReporter(actorId, profileId);
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
        // 外部URLや旧参照を取得せず、Profile所有者の管理下原本だけを証拠へコピーする。
        var image = profile.getIcon() == null ? null : profileImages
                .findOwnedSource(profile.getIcon(), owner.getId())
                .orElseThrow(() -> new ReportOperationException(Reason.FAILED, profileId));
        Report report = new Report();
        report.setReporterUser(reporter);
        report.setTargetType(ReportTargetType.PROFILE);
        report.setTargetId(profileId);
        report.setTargetOwnerUserId(owner.getId());
        report.setReason(request.reason());
        report.setDetail(request.detail());
        report = reports.saveAndFlush(report);
        profileSnapshots.saveAndFlush(new ReportProfileSnapshot(report, profile, image));
        retention.pending(com.example.bulletinboard.model.PrivacyRetentionRecord.Target.REPORT_EVIDENCE,
                report.getId().toString(),owner.getId(),report.getId());
    }

    /** 保持内容は管理者だけが参照する。画像取得時にもDB上のACTIVEを再確認する。 */
    @Transactional(readOnly = true)
    public EvidenceImage getEvidenceImage(Long reportId, String actorId) {
        privacyAccess.require(actorId);
        retention.requireEvidencePolicy(reportId);
        privacyAccess.auditRead(actorId,"REPORT_EVIDENCE",reportId.toString(),"READ_IMAGE");
        User admin = users.findById(ActorIds.parse(actorId))
                .orElseThrow(() -> new ReportOperationException(Reason.FORBIDDEN, reportId));
        if (admin.getAccountStatus() != AccountStatus.ACTIVE || !"ROLE_ADMIN".equals(admin.getRole())) {
            throw new ReportOperationException(Reason.FORBIDDEN, reportId);
        }
        Report report = reports.findById(reportId)
                .orElseThrow(() -> new ReportOperationException(Reason.NOT_FOUND, reportId));
        if (report.getTargetType() == ReportTargetType.PROFILE) {
            ReportProfileSnapshot snapshot = profileSnapshots.findById(reportId)
                    .orElseThrow(() -> new ReportOperationException(Reason.NOT_FOUND, reportId));
            byte[] content = snapshot.getImageContent();
            if (content == null || snapshot.getContentType() == null) {
                throw new ReportOperationException(Reason.NOT_FOUND, reportId);
            }
            return new EvidenceImage(content, snapshot.getContentType());
        }
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

    private User requireActiveReporter(String actorId, Long targetId) {
        User reporter = users.findById(ActorIds.parse(actorId))
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
