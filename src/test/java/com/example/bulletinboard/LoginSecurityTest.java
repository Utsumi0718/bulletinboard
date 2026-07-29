package com.example.bulletinboard;

import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional // テスト完了後にDB変更を自動ロールバック
class LoginSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        // テスト用のユーザーを1件作成
        User user = new User();
        user.setUsername("testuser");
        user.setPassword(passwordEncoder.encode("Password123"));
        user.setFailedAttempt(0);
        user.setAccountNonLocked(true);
        userRepository.save(user);
    }

    @Test
    @DisplayName("パスワード失敗1回目：/login?error=wrong にリダイレクトされ、失敗カウントが1になること")
    void loginFailure_WrongPassword_RedirectsToWrong() throws Exception {
        mockMvc.perform(post("/login")
                .param("username", "testuser")
                .param("password", "WrongPass1")
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=wrong"));

        User user = userRepository.findByUsername("testuser").orElseThrow();
        assertEquals(1, user.getFailedAttempt());
        assertTrue(user.isAccountNonLocked());
    }

    @Test
    @DisplayName("パスワード失敗3回目：アカウントがロックされ /login?error=locked にリダイレクトされること")
    void loginFailure_ThreeTimes_LocksAccountAndRedirectsToLocked() throws Exception {
        // 1回目の失敗
        mockMvc.perform(post("/login").param("username", "testuser").param("password", "Wrong1").with(csrf()));
        // 2回目の失敗
        mockMvc.perform(post("/login").param("username", "testuser").param("password", "Wrong2").with(csrf()));
        // 3回目の失敗（ここでロック発生）
        mockMvc.perform(post("/login").param("username", "testuser").param("password", "Wrong3").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=wrong"));//「パスワード間違い」扱いになる

      // DB上で失敗回数が3になり、ロック状態（false）になっていることを確認
        User user = userRepository.findByUsername("testuser").orElseThrow();
        assertEquals(3, user.getFailedAttempt());
        assertFalse(user.isAccountNonLocked()); // ロック状態（false）になっていること

      // 4回目のログイン試行（ここで初めてLockedExceptionが投げられる）
        mockMvc.perform(post("/login").param("username", "testuser").param("password", "Wrong4").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=locked")); // ★ 4回目で「ロックエラー」扱いになる
    }

    @Test
    @DisplayName("ロック中にログイン試行：パスワードが合っていても /login?error=locked にリダイレクトされること")
    void login_WhenLocked_RedirectsToLocked() throws Exception {
        // 事前にアカウントをロック状態にしておく
        User user = userRepository.findByUsername("testuser").orElseThrow();
        user.setFailedAttempt(3);
        user.setAccountNonLocked(false);
        userRepository.save(user);

        // 正しいパスワードで送信してもロックエラーになるか
        mockMvc.perform(post("/login")
                .param("username", "testuser")
                .param("password", "Password123")
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=locked"));
    }
}