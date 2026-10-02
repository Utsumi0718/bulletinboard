package com.example.bulletinboard.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mail.MailSendException;

import com.example.bulletinboard.dto.auth.PasswordResetConfirmRequest;
import com.example.bulletinboard.exception.PasswordResetException;

@ExtendWith(MockitoExtension.class)
class PasswordResetSubmissionServiceTest {
    @Mock PasswordResetService reset;
    @Mock PasswordResetNotificationService notification;
    PasswordResetSubmissionService submission;

    @BeforeEach
    void setUp() {
        submission = new PasswordResetSubmissionService(reset, notification);
    }

    @Test
    void unknownEmailDoesNotSendMail() {
        when(reset.issue("unknown@example.com")).thenReturn(Optional.empty());
        submission.request("unknown@example.com");
        verify(notification, never()).send(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void issueDatabaseFailureIsConvertedToSafeFailure() {
        var cause = new DataAccessResourceFailureException("SELECT secret@example.com");
        when(reset.issue("secret@example.com")).thenThrow(cause);
        assertReason(() -> submission.request("secret@example.com"),
                PasswordResetException.Reason.FAILED);
    }

    @Test
    void mailFailureRevokesIssuedToken() {
        var issued = new PasswordResetService.IssuedToken("member@example.com", "raw-secret-token");
        when(reset.issue(issued.email())).thenReturn(Optional.of(issued));
        doThrow(new MailSendException("smtp secret" )).when(notification).send(issued);

        assertReason(() -> submission.request(issued.email()),
                PasswordResetException.Reason.MAIL_UNAVAILABLE);
        verify(reset).revoke(issued.rawToken());
    }

    @Test
    void confirmDatabaseFailureIsConvertedToSafeFailure() {
        var request = new PasswordResetConfirmRequest("raw-token", "Password123");
        doThrow(new DataAccessResourceFailureException("UPDATE password='secret'"))
                .when(reset).confirm(request.token(), request.newPassword());
        assertReason(() -> submission.confirm(request), PasswordResetException.Reason.FAILED);
    }

    @Test
    void invalidTokenReasonIsPreserved() {
        var request = new PasswordResetConfirmRequest("invalid", "Password123");
        doThrow(new PasswordResetException(PasswordResetException.Reason.INVALID))
                .when(reset).confirm(request.token(), request.newPassword());
        assertReason(() -> submission.confirm(request), PasswordResetException.Reason.INVALID);
    }

    private void assertReason(Runnable action, PasswordResetException.Reason reason) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(PasswordResetException.class,
                        ex -> org.assertj.core.api.Assertions.assertThat(ex.getReason()).isEqualTo(reason));
    }
}
