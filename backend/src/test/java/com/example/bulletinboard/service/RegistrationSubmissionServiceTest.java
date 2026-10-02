package com.example.bulletinboard.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.example.bulletinboard.dto.auth.RegisterRequest;
import com.example.bulletinboard.exception.RegistrationConflictException;
import com.example.bulletinboard.exception.RegistrationConflictException.Field;
import com.example.bulletinboard.exception.RegistrationOperationException;
import com.example.bulletinboard.repository.UserRepository;

/** 保存トランザクション終了後の重複判定と障害変換を確認する。 */
@ExtendWith(MockitoExtension.class)
class RegistrationSubmissionServiceTest {
    private static final RegisterRequest REQUEST =
            new RegisterRequest("member01", "member@example.com", "Password123");

    @Mock RegistrationService registration;
    @Mock UserRepository users;
    private RegistrationSubmissionService submission;

    @BeforeEach
    void setUp() {
        submission = new RegistrationSubmissionService(registration, users);
        when(registration.register(REQUEST))
                .thenThrow(new DataIntegrityViolationException("internal constraint detail"));
    }

    @Test
    void committedUsernameFromConcurrentRequestBecomesUsernameConflict() {
        when(users.existsByUsername(REQUEST.username())).thenReturn(true);
        assertThatThrownBy(() -> submission.register(REQUEST))
                .isInstanceOf(RegistrationConflictException.class)
                .extracting(ex -> ((RegistrationConflictException) ex).getField())
                .isEqualTo(Field.USERNAME);
    }

    @Test
    void committedEmailFromConcurrentRequestBecomesEmailConflict() {
        when(users.existsByUsername(REQUEST.username())).thenReturn(false);
        when(users.existsByEmail(REQUEST.email())).thenReturn(true);
        assertThatThrownBy(() -> submission.register(REQUEST))
                .isInstanceOf(RegistrationConflictException.class)
                .extracting(ex -> ((RegistrationConflictException) ex).getField())
                .isEqualTo(Field.EMAIL);
    }

    @Test
    void unrelatedDatabaseFailureBecomesRegistrationOperationFailure() {
        when(users.existsByUsername(REQUEST.username())).thenReturn(false);
        when(users.existsByEmail(REQUEST.email())).thenReturn(false);
        assertThatThrownBy(() -> submission.register(REQUEST))
                .isInstanceOf(RegistrationOperationException.class)
                .hasCauseInstanceOf(DataIntegrityViolationException.class)
                .hasMessageNotContaining("internal constraint detail");
    }
}
