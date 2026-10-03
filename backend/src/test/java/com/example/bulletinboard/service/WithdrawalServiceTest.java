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
    WithdrawalService service;

    @BeforeEach
    void setUp() {
        service = new WithdrawalService(users, passwords);
    }

    @Test
    void correctPasswordWithdrawsWithoutChangingLoginLock() {
        User user = user(AccountStatus.ACTIVE);
        user.setFailedAttempt(2);
        user.setAccountNonLocked(false);
        when(users.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(passwords.matches("Password123", "encoded")).thenReturn(true);

        service.withdraw(user.getEmail(), "Password123");

        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.WITHDRAWN);
        assertThat(user.getWithdrawnAt()).isNotNull();
        assertThat(user.getFailedAttempt()).isEqualTo(2);
        assertThat(user.isAccountNonLocked()).isFalse();
        verify(users).saveAndFlush(user);
    }

    @Test
    void wrongPasswordDoesNotWithdraw() {
        User user = user(AccountStatus.ACTIVE);
        when(users.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(passwords.matches("wrong", "encoded")).thenReturn(false);

        assertReason(() -> service.withdraw(user.getEmail(), "wrong"),
                WithdrawalException.Reason.PASSWORD_MISMATCH);
        verify(users, never()).saveAndFlush(user);
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"FROZEN", "WITHDRAWN"})
    void inactiveAccountCannotWithdraw(AccountStatus status) {
        User user = user(status);
        when(users.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        assertReason(() -> service.withdraw(user.getEmail(), "Password123"),
                WithdrawalException.Reason.FORBIDDEN);
        verify(passwords, never()).matches(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void missingUserCannotWithdraw() {
        when(users.findByEmailForUpdate("missing@example.com")).thenReturn(Optional.empty());
        assertReason(() -> service.withdraw("missing@example.com", "Password123"),
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
