package com.example.bulletinboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import java.sql.SQLException;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DataIntegrityViolationException;

import com.example.bulletinboard.dto.report.TopicReportRequest;
import com.example.bulletinboard.exception.ReportOperationException;
import com.example.bulletinboard.exception.ReportOperationException.Reason;
import com.example.bulletinboard.model.ReportReason;
import com.example.bulletinboard.model.ReportTargetType;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.ReportRepository;
import com.example.bulletinboard.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ReportSubmissionServiceTest {
    private static final long TOPIC_ID = 31L;
    private static final String EMAIL = "reporter@example.com";
    private static final TopicReportRequest REQUEST = new TopicReportRequest(ReportReason.SPAM, null);

    @Mock ReportService reportService;
    @Mock ReportRepository reports;
    @Mock UserRepository users;
    private ReportSubmissionService submission;

    @BeforeEach
    void setUp() {
        submission = new ReportSubmissionService(reportService, reports, users);
    }

    static Object[][] uniqueErrors() {
        return new Object[][] {
                {new SQLException("fake SQL content", "23505", 0)},
                {new SQLException("fake SQL content", "23000", 1062)}
        };
    }

    @ParameterizedTest
    @MethodSource("uniqueErrors")
    void committedDuplicateAfterUniqueConstraintFailureIsConflict(SQLException sql) {
        doThrow(failure(sql)).when(reportService).submitTopic(TOPIC_ID, EMAIL, REQUEST);
        User reporter = new User();
        reporter.setId(7L);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(reporter));
        when(reports.existsByReporterUserIdAndTargetTypeAndTargetId(7L, ReportTargetType.TOPIC, TOPIC_ID))
                .thenReturn(true);

        assertThatThrownBy(() -> submission.submitTopic(TOPIC_ID, EMAIL, REQUEST))
                .isInstanceOfSatisfying(ReportOperationException.class,
                        ex -> assertThat(ex.getReason()).isEqualTo(Reason.DUPLICATE));
        verify(reports).existsByReporterUserIdAndTargetTypeAndTargetId(7L, ReportTargetType.TOPIC, TOPIC_ID);
    }

    @Test
    void unrelatedConstraintFailureIsServerErrorEvenWhenMessageMentionsDuplicate() {
        doThrow(failure(new SQLException("duplicate report fake secret", "23514", 0)))
                .when(reportService).submitTopic(TOPIC_ID, EMAIL, REQUEST);
        assertThatThrownBy(() -> submission.submitTopic(TOPIC_ID, EMAIL, REQUEST))
                .isInstanceOfSatisfying(ReportOperationException.class,
                        ex -> assertThat(ex.getReason()).isEqualTo(Reason.FAILED));
    }

    @Test
    void uniqueFailureWithoutCommittedMatchingReportIsServerError() {
        doThrow(failure(new SQLException("fake other unique index", "23505", 0)))
                .when(reportService).submitTopic(TOPIC_ID, EMAIL, REQUEST);
        User reporter = new User();
        reporter.setId(7L);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(reporter));
        when(reports.existsByReporterUserIdAndTargetTypeAndTargetId(7L, ReportTargetType.TOPIC, TOPIC_ID))
                .thenReturn(false);

        assertThatThrownBy(() -> submission.submitTopic(TOPIC_ID, EMAIL, REQUEST))
                .isInstanceOfSatisfying(ReportOperationException.class,
                        ex -> assertThat(ex.getReason()).isEqualTo(Reason.FAILED));
    }

    private DataIntegrityViolationException failure(SQLException sql) {
        return new DataIntegrityViolationException("fake database error", sql);
    }
}
