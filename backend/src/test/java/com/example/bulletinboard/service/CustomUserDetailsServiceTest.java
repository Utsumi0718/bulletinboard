package com.example.bulletinboard.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.bulletinboard.model.AccountStatus;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.UserRepository;

/**
 * 【クラス全体の役割】
 * CustomUserDetailsServiceのパスワード再設定処理を検証する
 * Service層の単体テストクラスです。
 *
 * 【主な検証内容】
 * - emailを基準に対象ユーザーを検索すること
 * - 新しいパスワードがPasswordEncoderでハッシュ化されること
 * - ログイン失敗によるセキュリティロック中の場合、
 *   パスワード再設定によってaccountNonLocked=true、
 *   failedAttempt=0へ戻ること
 * - FROZENユーザーがパスワード再設定しても
 *   accountStatus=FROZENのままであること
 * - WITHDRAWNユーザーがパスワード再設定しても
 *   accountStatus=WITHDRAWNのままであること
 *
 * 【設計上のポイント】
 * パスワード再設定で解除するのは、
 * failedAttemptによるセキュリティロックのみです。
 *
 * 管理者によるFROZENや退会済みのWITHDRAWNは、
 * パスワード再設定によってACTIVEへ戻しません。
 */
@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private CustomUserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        userDetailsService =
                new CustomUserDetailsService(
                        userRepository,
                        passwordEncoder
                );
    }

    @Test
    @DisplayName("セキュリティロック中にパスワード再設定するとロックが解除される")
    void updatePassword_WhenSecurityLocked_ShouldUnlockAccount() {

        User user = new User();
        user.setEmail("test@example.com");
        user.setPassword("oldEncodedPassword");
        user.setFailedAttempt(3);
        user.setAccountNonLocked(false);
        user.setAccountStatus(AccountStatus.ACTIVE);

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.encode("NewPassword123"))
                .thenReturn("newEncodedPassword");

        boolean result = userDetailsService.updatePassword(
                "test@example.com",
                "NewPassword123"
        );

        assertTrue(result);
        assertEquals("newEncodedPassword", user.getPassword());
        assertEquals(0, user.getFailedAttempt());
        assertTrue(user.isAccountNonLocked());

        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("FROZENユーザーがパスワード再設定しても凍結状態は維持される")
    void updatePassword_WhenFrozen_ShouldKeepFrozenStatus() {

        User user = new User();
        user.setEmail("frozen@example.com");
        user.setPassword("oldEncodedPassword");
        user.setFailedAttempt(0);
        user.setAccountNonLocked(true);
        user.setAccountStatus(AccountStatus.FROZEN);

        when(userRepository.findByEmail("frozen@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.encode("NewPassword123"))
                .thenReturn("newEncodedPassword");

        boolean result = userDetailsService.updatePassword(
                "frozen@example.com",
                "NewPassword123"
        );

        assertTrue(result);
        assertEquals(
                AccountStatus.FROZEN,
                user.getAccountStatus()
        );
        assertEquals("newEncodedPassword", user.getPassword());

        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("WITHDRAWNユーザーがパスワード再設定しても退会状態は維持される")
    void updatePassword_WhenWithdrawn_ShouldKeepWithdrawnStatus() {

        User user = new User();
        user.setEmail("withdrawn@example.com");
        user.setPassword("oldEncodedPassword");
        user.setFailedAttempt(0);
        user.setAccountNonLocked(true);
        user.setAccountStatus(AccountStatus.WITHDRAWN);

        when(userRepository.findByEmail("withdrawn@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.encode("NewPassword123"))
                .thenReturn("newEncodedPassword");

        boolean result = userDetailsService.updatePassword(
                "withdrawn@example.com",
                "NewPassword123"
        );

        assertTrue(result);
        assertEquals(
                AccountStatus.WITHDRAWN,
                user.getAccountStatus()
        );
        assertEquals("newEncodedPassword", user.getPassword());

        verify(userRepository).save(user);
    }
}