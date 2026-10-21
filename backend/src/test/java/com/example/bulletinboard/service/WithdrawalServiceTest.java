package com.example.bulletinboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.bulletinboard.exception.WithdrawalException;
import com.example.bulletinboard.model.AccountStatus;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class WithdrawalServiceTest {
    @Mock UserRepository users;
    @Mock PasswordEncoder passwords;
    @Mock com.example.bulletinboard.repository.ProfileRepository profiles;
    @Mock com.example.bulletinboard.repository.PasswordResetTokenRepository tokens;
    @Mock com.example.bulletinboard.repository.ProfileImageRepository images;
    @Mock PrivacyRetentionService retention;
    @Mock com.example.bulletinboard.repository.WithdrawalTaskRepository tasks;
    @Mock org.springframework.context.ApplicationEventPublisher events;
    @Mock jakarta.persistence.EntityManager entityManager;
    WithdrawalService service;

    @BeforeEach
    void setUp() {
        service = new WithdrawalService(users, passwords,profiles,tokens,images,retention,tasks,events,entityManager);
    }

    @Test
    void correctPasswordErasesCredentialsAndResetsLoginLock() {
        User user = user(AccountStatus.ACTIVE);
        user.setFailedAttempt(2);
        user.setAccountNonLocked(false);
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(passwords.matches("Password123", "encoded")).thenReturn(true);
        when(passwords.encode(org.mockito.ArgumentMatchers.anyString())).thenReturn("replacement");

        service.withdraw("1", "Password123");

        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.WITHDRAWN);
        assertThat(user.getWithdrawnAt()).isNotNull();
        assertThat(user.getFailedAttempt()).isZero();
        assertThat(user.isAccountNonLocked()).isTrue();
        assertThat(user.getPassword()).isEqualTo("replacement");
        assertThat(user.getEmail()).isEqualTo(WithdrawalIdentity.email(1L));
        verify(users).saveAndFlush(user);
    }

    @Test
    void wrongPasswordDoesNotWithdraw() {
        User user = user(AccountStatus.ACTIVE);
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(passwords.matches("wrong", "encoded")).thenReturn(false);

        assertReason(() -> service.withdraw("1", "wrong"),
                WithdrawalException.Reason.PASSWORD_MISMATCH);
        verify(users, never()).saveAndFlush(user);
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"FROZEN", "WITHDRAWN"})
    void inactiveAccountCannotWithdraw(AccountStatus status) {
        User user = user(status);
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        assertReason(() -> service.withdraw("1", "Password123"),
                WithdrawalException.Reason.FORBIDDEN);
        verify(passwords, never()).matches(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void missingUserCannotWithdraw() {
        when(users.findByIdForUpdate(2L)).thenReturn(Optional.empty());
        assertReason(() -> service.withdraw("2", "Password123"),
                WithdrawalException.Reason.FORBIDDEN);
    }

    @Test
    void submissionConvertsDatabaseFailure() {
        WithdrawalService transactional = org.mockito.Mockito.mock(WithdrawalService.class);
        doThrowDatabaseFailure(transactional);
        var submission = new WithdrawalSubmissionService(transactional);
        assertReason(() -> submission.withdraw("member@example.com", "Password123"),
                WithdrawalException.Reason.FAILED);
    }

    private void doThrowDatabaseFailure(WithdrawalService transactional) {
        org.mockito.Mockito.doThrow(new DataAccessResourceFailureException("secret SQL"))
                .when(transactional).withdraw("member@example.com", "Password123");
    }

    private User user(AccountStatus status) {
        User user = new User();
        user.setId(1L);
        user.setEmail("member@example.com");
        user.setPassword("encoded");
        user.setAccountStatus(status);
        return user;
    }

    private void assertReason(Runnable action, WithdrawalException.Reason reason) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(WithdrawalException.class,
                        ex -> assertThat(ex.getReason()).isEqualTo(reason));
    }
}
