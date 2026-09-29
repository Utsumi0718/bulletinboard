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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.bulletinboard.dto.report.ReportRequest;
import com.example.bulletinboard.exception.ReportOperationException;
import com.example.bulletinboard.exception.ReportOperationException.Reason;
import com.example.bulletinboard.model.AccountStatus;
import com.example.bulletinboard.model.Answer;
import com.example.bulletinboard.model.Report;
import com.example.bulletinboard.model.ReportAnswerSnapshot;
import com.example.bulletinboard.model.ReportReason;
import com.example.bulletinboard.model.ReportStatus;
import com.example.bulletinboard.model.ReportTargetType;
import com.example.bulletinboard.model.Topic;
import com.example.bulletinboard.model.TopicImage;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.AnswerRepository;
import com.example.bulletinboard.repository.ReportAnswerSnapshotRepository;
import com.example.bulletinboard.repository.ReportRepository;
import com.example.bulletinboard.repository.ReportTopicSnapshotRepository;
import com.example.bulletinboard.repository.TopicRepository;
import com.example.bulletinboard.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ReportAnswerServiceTest {
    private static final long ANSWER_ID = 44L;
    private static final long TOPIC_ID = 18L;
    private static final String EMAIL = "reporter@example.com";
    private static final String IMAGE_URL = "/api/topic-images/11111111-1111-1111-1111-111111111111";
    private static final ReportRequest REQUEST = new ReportRequest(ReportReason.SPAM, null);

    @Mock UserRepository users;
    @Mock TopicRepository topics;
    @Mock AnswerRepository answers;
    @Mock TopicImageService images;
    @Mock ReportRepository reports;
    @Mock ReportTopicSnapshotRepository topicSnapshots;
    @Mock ReportAnswerSnapshotRepository answerSnapshots;
    @InjectMocks ReportService service;

    @Test
    void savesAnswerAndParentEvidenceWithServerChosenOwner() {
        happyCase();
        service.submitAnswer(ANSWER_ID, EMAIL, REQUEST);
        var report = ArgumentCaptor.forClass(Report.class);
        verify(reports).saveAndFlush(report.capture());
        assertThat(report.getValue().getTargetType()).isEqualTo(ReportTargetType.ANSWER);
        assertThat(report.getValue().getTargetId()).isEqualTo(ANSWER_ID);
        assertThat(report.getValue().getTargetOwnerUserId()).isEqualTo(22L);
        assertThat(report.getValue().getReporterUser().getId()).isEqualTo(11L);
        assertThat(report.getValue().getStatus()).isEqualTo(ReportStatus.UNHANDLED);
        var snapshot = ArgumentCaptor.forClass(ReportAnswerSnapshot.class);
        verify(answerSnapshots).saveAndFlush(snapshot.capture());
        assertThat(snapshot.getValue().getAnswerContent()).isEqualTo("通報時の回答");
        assertThat(snapshot.getValue().getTopicId()).isEqualTo(TOPIC_ID);
        assertThat(snapshot.getValue().getTopicTitle()).isEqualTo("親タイトル");
        assertThat(snapshot.getValue().getTopicQuestion()).isEqualTo("親本文");
        assertThat(snapshot.getValue().getImageContent()).containsExactly(1, 2, 3);
        verify(topicSnapshots, never()).saveAndFlush(any());
    }

    @Test
    void missingAnswerIsNotFound() {
        activeReporter();
        when(answers.findReportableTopicIdByAnswerId(ANSWER_ID)).thenReturn(Optional.empty());
        expect(Reason.NOT_FOUND);
    }

    @Test
    void deletedParentIsNotReportable() {
        activeReporter();
        when(answers.findReportableTopicIdByAnswerId(ANSWER_ID)).thenReturn(Optional.of(TOPIC_ID));
        when(topics.findReportableByIdForUpdate(TOPIC_ID)).thenReturn(Optional.empty());
        expect(Reason.NOT_FOUND);
        verify(answers, never()).findReportableByIdForUpdate(ANSWER_ID);
    }

    @Test
    void deletedAnswerAfterInitialReadIsNotReportable() {
        activeReporter();
        when(answers.findReportableTopicIdByAnswerId(ANSWER_ID)).thenReturn(Optional.of(TOPIC_ID));
        when(topics.findReportableByIdForUpdate(TOPIC_ID)).thenReturn(Optional.of(topic()));
        when(answers.findReportableByIdForUpdate(ANSWER_ID)).thenReturn(Optional.empty());
        expect(Reason.NOT_FOUND);
    }

    @Test
    void selfReportIsForbidden() {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user(22L, EMAIL, AccountStatus.ACTIVE)));
        lookupAnswerAndParent();
        expect(Reason.FORBIDDEN);
    }

    @Test
    void duplicateAnswerIsRejected() {
        activeReporter();
        lookupAnswerAndParent();
        when(reports.existsByReporterUserIdAndTargetTypeAndTargetId(11L, ReportTargetType.ANSWER, ANSWER_ID))
                .thenReturn(true);
        expect(Reason.DUPLICATE);
        verify(images, never()).findOwnedSource(any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"FROZEN", "WITHDRAWN"})
    void inactiveReporterIsForbidden(AccountStatus status) {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user(11L, EMAIL, status)));
        expect(Reason.FORBIDDEN);
        verify(answers, never()).findReportableTopicIdByAnswerId(ANSWER_ID);
    }

    @Test
    void missingParentImageFailsWithoutSaving() {
        activeReporter();
        lookupAnswerAndParent();
        when(images.findOwnedSource(IMAGE_URL, 33L)).thenReturn(Optional.empty());
        expect(Reason.FAILED);
        verify(reports, never()).saveAndFlush(any());
    }

    @Test
    void blankOtherDetailIsInvalidBeforeDatabaseRead() {
        assertThatThrownBy(() -> service.submitAnswer(ANSWER_ID, EMAIL,
                new ReportRequest(ReportReason.OTHER, "  ")))
                .isInstanceOfSatisfying(ReportOperationException.class,
                        ex -> assertThat(ex.getReason()).isEqualTo(Reason.INVALID));
        verify(users, never()).findByEmail(any());
    }

    private void expect(Reason reason) {
        assertThatThrownBy(() -> service.submitAnswer(ANSWER_ID, EMAIL, REQUEST))
                .isInstanceOfSatisfying(ReportOperationException.class,
                        ex -> assertThat(ex.getReason()).isEqualTo(reason));
    }

    private void happyCase() {
        activeReporter();
        lookupAnswerAndParent();
        when(images.findOwnedSource(IMAGE_URL, 33L))
                .thenReturn(Optional.of(new TopicImage(33L, "image/png", new byte[]{1, 2, 3})));
        when(reports.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    private void activeReporter() {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user(11L, EMAIL, AccountStatus.ACTIVE)));
    }

    private void lookupAnswerAndParent() {
        when(answers.findReportableTopicIdByAnswerId(ANSWER_ID)).thenReturn(Optional.of(TOPIC_ID));
        when(topics.findReportableByIdForUpdate(TOPIC_ID)).thenReturn(Optional.of(topic()));
        when(answers.findReportableByIdForUpdate(ANSWER_ID)).thenReturn(Optional.of(answer()));
    }

    private Answer answer() {
        Answer result = new Answer();
        result.setId(ANSWER_ID);
        result.setTopic(topic());
        result.setUser(user(22L, "owner@example.com", AccountStatus.ACTIVE));
        result.setContent("通報時の回答");
        return result;
    }

    private Topic topic() {
        Topic result = new Topic();
        result.setId(TOPIC_ID);
        result.setUser(user(33L, "topic-owner@example.com", AccountStatus.ACTIVE));
        result.setTitle("親タイトル");
        result.setQuestion("親本文");
        result.setImage(IMAGE_URL);
        return result;
    }

    private User user(Long id, String email, AccountStatus status) {
        User result = new User();
        result.setId(id);
        result.setEmail(email);
        result.setAccountStatus(status);
        return result;
    }
}
