package com.example.bulletinboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.bulletinboard.model.AccountStatus;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.UserRepository;

/**
 * 【クラス全体の役割】
 * email + passwordによるログイン認証と、
 * ログイン失敗回数によるセキュリティロックを検証するテストクラスです。
 *
 * 【主な検証内容】
 * - パスワード誤り時にfailedAttemptが加算されること
 * - ログイン失敗3回でaccountNonLocked=falseになること
 * - ロック状態では正しいパスワードでもログインできないこと
 *
 * accountStatusについては、
 * このテストでは通常利用可能なACTIVEを使用します。
 * FROZEN / WITHDRAWNのログイン拒否については別テストで検証します。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LoginSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {

        // 通常利用可能なテストユーザーを作成
        User user = new User();
        user.setUsername("testuser");
        user.setEmail("testuser@example.com");
        user.setPassword(
                passwordEncoder.encode("Password123")
        );
        user.setFailedAttempt(0);
        user.setAccountNonLocked(true);
        user.setAccountStatus(AccountStatus.ACTIVE);

        userRepository.save(user);
    }

    @Test
    @DisplayName(
        "パスワード失敗1回目：wrongへリダイレクトされ、失敗回数が1になる"
    )
    void loginFailure_WrongPassword_RedirectsToWrong()
            throws Exception {

        mockMvc.perform(post("/login")
                .param("email", "testuser@example.com")
                .param("password", "WrongPass1")
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(
                    redirectedUrl("/login?error=wrong")
                );

        User user = userRepository
                .findByEmail("testuser@example.com")
                .orElseThrow();

        assertEquals(1, user.getFailedAttempt());
        assertTrue(user.isAccountNonLocked());
    }

    @Test
    @DisplayName(
        "パスワードを3回間違えるとロックされ、4回目はlockedへリダイレクトされる"
    )
    void loginFailure_ThreeTimes_LocksAccountAndRedirectsToLocked()
            throws Exception {

        // 1回目
        mockMvc.perform(post("/login")
                .param("email", "testuser@example.com")
                .param("password", "Wrong1")
                .with(csrf()));

        // 2回目
        mockMvc.perform(post("/login")
                .param("email", "testuser@example.com")
                .param("password", "Wrong2")
                .with(csrf()));

        /*
         * 3回目。
         * この認証試行自体はBadCredentials扱いですが、
         * AuthenticationEventListenerによって
         * failedAttempt=3、accountNonLocked=falseになります。
         */
        mockMvc.perform(post("/login")
                .param("email", "testuser@example.com")
                .param("password", "Wrong3")
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(
                    redirectedUrl("/login?error=wrong")
                );

        User user = userRepository
                .findByEmail("testuser@example.com")
                .orElseThrow();

        assertEquals(3, user.getFailedAttempt());
        assertFalse(user.isAccountNonLocked());

        /*
         * 4回目は認証開始時点ですでにロック状態なので、
         * LockedExceptionとなります。
         */
        mockMvc.perform(post("/login")
                .param("email", "testuser@example.com")
                .param("password", "Wrong4")
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(
                    redirectedUrl("/login?error=locked")
                );
    }

    @Test
    @DisplayName(
        "セキュリティロック中は正しいパスワードでもlockedへリダイレクトされる"
    )
    void login_WhenLocked_RedirectsToLocked()
            throws Exception {

        User user = userRepository
                .findByEmail("testuser@example.com")
                .orElseThrow();

        user.setFailedAttempt(3);
        user.setAccountNonLocked(false);

        userRepository.save(user);

        mockMvc.perform(post("/login")
                .param("email", "testuser@example.com")
                .param("password", "Password123")
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(
                    redirectedUrl("/login?error=locked")
                );
    }
}