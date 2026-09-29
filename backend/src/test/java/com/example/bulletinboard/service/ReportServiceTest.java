package com.example.bulletinboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.bulletinboard.dto.report.ReportRequest;
import com.example.bulletinboard.exception.ReportOperationException;
import com.example.bulletinboard.exception.ReportOperationException.Reason;
import com.example.bulletinboard.model.AccountStatus;
import com.example.bulletinboard.model.Report;
import com.example.bulletinboard.model.ReportReason;
import com.example.bulletinboard.model.ReportStatus;
import com.example.bulletinboard.model.ReportTargetType;
import com.example.bulletinboard.model.ReportTopicSnapshot;
import com.example.bulletinboard.model.Topic;
import com.example.bulletinboard.model.TopicImage;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.ReportRepository;
import com.example.bulletinboard.repository.ReportTopicSnapshotRepository;
import com.example.bulletinboard.repository.TopicRepository;
import com.example.bulletinboard.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {
    private static final long TOPIC_ID = 44L;
    private static final String EMAIL = "reporter@example.com";
    private static final String IMAGE_URL = "/api/topic-images/11111111-1111-1111-1111-111111111111";
    @Mock UserRepository users;
    @Mock TopicRepository topics;
    @Mock TopicImageService images;
    @Mock ReportRepository reports;
    @Mock ReportTopicSnapshotRepository snapshots;
    @InjectMocks ReportService service;

    @ParameterizedTest
    @EnumSource(ReportReason.class)
    void acceptsEachReasonAndKeepsEvidencePrivateInDatabase(ReportReason reason) {
        happyCase("ROLE_USER", AccountStatus.ACTIVE);
        service.submitTopic(TOPIC_ID, EMAIL, new ReportRequest(reason, "detail"));
        var saved = ArgumentCaptor.forClass(Report.class);
        verify(reports).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getTargetType()).isEqualTo(ReportTargetType.TOPIC);
        assertThat(saved.getValue().getTargetId()).isEqualTo(TOPIC_ID);
        assertThat(saved.getValue().getTargetOwnerUserId()).isEqualTo(22L);
        assertThat(saved.getValue().getReporterUser().getId()).isEqualTo(11L);
        assertThat(saved.getValue().getReason()).isEqualTo(reason);
        assertThat(saved.getValue().getStatus()).isEqualTo(ReportStatus.UNHANDLED);
        var evidence = ArgumentCaptor.forClass(ReportTopicSnapshot.class);
        verify(snapshots).saveAndFlush(evidence.capture());
        assertThat(evidence.getValue().getTitle()).isEqualTo("撮影時のタイトル");
        assertThat(evidence.getValue().getQuestion()).isEqualTo("撮影時の本文");
        assertThat(evidence.getValue().getImageContent()).containsExactly(1, 2, 3);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsBlankOtherDetail(String detail) {
        rejectInput(new ReportRequest(ReportReason.OTHER, detail));
    }

    @Test
    void acceptsExactly500Characters() {
        happyCase("ROLE_USER", AccountStatus.ACTIVE);
        service.submitTopic(TOPIC_ID, EMAIL, new ReportRequest(ReportReason.OTHER, "a".repeat(500)));
        verify(reports).saveAndFlush(any());
    }

    @Test
    void rejects501CharactersForAnyReason() {
        rejectInput(new ReportRequest(ReportReason.SPAM, "a".repeat(501)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_USER", "ROLE_ADMIN"})
    void activeUserOrAdminCanSubmit(String role) {
        happyCase(role, AccountStatus.ACTIVE);
        service.submitTopic(TOPIC_ID, EMAIL, new ReportRequest(ReportReason.ABUSE, null));
        verify(reports).saveAndFlush(any());
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"FROZEN", "WITHDRAWN"})
    void serviceRejectsInactiveReporterEvenWithAuthenticatedRequest(AccountStatus status) {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user(11L, EMAIL, "ROLE_USER", status)));
        expect(Reason.FORBIDDEN);
        verify(topics, never()).findReportableByIdForUpdate(any());
    }

    @Test
    void rejectsSelfReport() {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user(22L, EMAIL, "ROLE_USER", AccountStatus.ACTIVE)));
        when(topics.findReportableByIdForUpdate(TOPIC_ID)).thenReturn(Optional.of(topic(AccountStatus.ACTIVE)));
        expect(Reason.FORBIDDEN);
        verify(reports, never()).saveAndFlush(any());
    }

    @Test
    void missingReporterIsForbidden() {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());
        expect(Reason.FORBIDDEN);
    }

    @Test
    void missingOrDeletedTopicIsNotReportable() {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user(11L, EMAIL, "ROLE_USER", AccountStatus.ACTIVE)));
        when(topics.findReportableByIdForUpdate(TOPIC_ID)).thenReturn(Optional.empty());
        expect(Reason.NOT_FOUND);
    }

    @Test
    void duplicateIsRejectedBeforeEvidenceRead() {
        reporterAndTopic(AccountStatus.ACTIVE);
        when(reports.existsByReporterUserIdAndTargetTypeAndTargetId(11L, ReportTargetType.TOPIC, TOPIC_ID))
                .thenReturn(true);
        expect(Reason.DUPLICATE);
        verify(images, never()).findOwnedSource(any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"FROZEN", "WITHDRAWN"})
    void stillAcceptsPublicTopicFromInactiveOwner(AccountStatus ownerStatus) {
        happyCase("ROLE_USER", ownerStatus);
        service.submitTopic(TOPIC_ID, EMAIL, new ReportRequest(ReportReason.SPAM, null));
        verify(reports).saveAndFlush(any());
    }

    private void rejectInput(ReportRequest request) {
        assertThatThrownBy(() -> service.submitTopic(TOPIC_ID, EMAIL, request))
                .isInstanceOfSatisfying(ReportOperationException.class,
                        ex -> assertThat(ex.getReason()).isEqualTo(Reason.INVALID));
        verify(users, never()).findByEmail(any());
    }

    private void expect(Reason reason) {
        assertThatThrownBy(() -> service.submitTopic(TOPIC_ID, EMAIL,
                new ReportRequest(ReportReason.SPAM, null)))
                .isInstanceOfSatisfying(ReportOperationException.class,
                        ex -> assertThat(ex.getReason()).isEqualTo(reason));
    }

    private void reporterAndTopic(AccountStatus ownerStatus) {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user(11L, EMAIL, "ROLE_USER", AccountStatus.ACTIVE)));
        when(topics.findReportableByIdForUpdate(TOPIC_ID)).thenReturn(Optional.of(topic(ownerStatus)));
    }

    private void happyCase(String role, AccountStatus ownerStatus) {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user(11L, EMAIL, role, AccountStatus.ACTIVE)));
        when(topics.findReportableByIdForUpdate(TOPIC_ID)).thenReturn(Optional.of(topic(ownerStatus)));
        when(images.findOwnedSource(IMAGE_URL, 22L))
                .thenReturn(Optional.of(new TopicImage(22L, "image/png", new byte[]{1, 2, 3})));
        when(reports.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Topic topic(AccountStatus ownerStatus) {
        Topic topic = new Topic();
        topic.setId(TOPIC_ID);
        topic.setUser(user(22L, "owner@example.com", "ROLE_USER", ownerStatus));
        topic.setTitle("撮影時のタイトル");
        topic.setQuestion("撮影時の本文");
        topic.setImage(IMAGE_URL);
        return topic;
    }

    private User user(Long id, String email, String role, AccountStatus status) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setRole(role);
        user.setAccountStatus(status);
        return user;
    }
}
