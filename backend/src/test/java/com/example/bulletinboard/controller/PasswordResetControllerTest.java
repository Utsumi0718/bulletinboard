package com.example.bulletinboard.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.bulletinboard.repository.UserRepository;
import com.example.bulletinboard.security.SecurityConfig;
import com.example.bulletinboard.service.CustomUserDetailsService;

/**
 * 【クラス全体の役割】
 * PasswordResetControllerのWeb層における
 * パスワード再設定処理を検証するテストクラスです。
 *
 * 【主な検証内容】
 * - emailを基準にパスワード再設定処理が行われること
 * - 正常な入力の場合にCustomUserDetailsService#updatePassword()が呼ばれること
 * - パスワード再設定成功後にログイン画面へリダイレクトされること
 * - 存在しないemailを指定した場合に再設定画面へ戻ること
 * - emailフィールドに適切なエラーが設定されること
 * - email形式や新しいパスワードが不正な場合に
 *   バリデーションエラーとなること
 * - バリデーションエラー時にはService層の更新処理を呼び出さないこと
 *
 * 【テスト方針】
 * - MockMvcを使用してPOST /reset-passwordへの
 *   HTTPリクエストを疑似的に送信します。
 * - CustomUserDetailsServiceはMockitoでモック化し、
 *   Controllerの入力検証・Service呼び出し・画面遷移に
 *   焦点を当てて検証します。
 * - Spring SecurityのCSRF対策を通過させるため、
 *   POSTリクエストには.with(csrf())を付与します。
 *
 * 【認証仕様との関係】
 * - パスワード再設定対象のユーザーはusernameではなくemailで特定します。
 * - accountNonLockedやaccountStatusそのものの更新ロジックは
 *   CustomUserDetailsService側の責務として別テストで検証します。
 */

@WebMvcTest(PasswordResetController.class)
@Import(SecurityConfig.class)
class PasswordResetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    @DisplayName("メールアドレスでパスワード再設定に成功するとログイン画面へリダイレクトされる")
    void resetPassword_Success_ShouldRedirectToLogin() throws Exception {

        when(userDetailsService.updatePassword(
                "test@example.com",
                "Password123"
        )).thenReturn(true);

        mockMvc.perform(post("/reset-password")
                .param("email", "test@example.com")
                .param("newPassword", "Password123")
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(
                    redirectedUrl("/login?reset_success")
                );

        verify(userDetailsService).updatePassword(
                "test@example.com",
                "Password123"
        );
    }

    @Test
    @DisplayName("存在しないメールアドレスでは再設定画面に戻りemailエラーになる")
    void resetPassword_EmailNotFound_ShouldReturnResetView()
            throws Exception {

        when(userDetailsService.updatePassword(
                "unknown@example.com",
                "Password123"
        )).thenReturn(false);

        mockMvc.perform(post("/reset-password")
                .param("email", "unknown@example.com")
                .param("newPassword", "Password123")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/reset-password"))
                .andExpect(
                    model().attributeHasFieldErrorCode(
                        "resetPasswordForm",
                        "email",
                        "error.email"
                    )
                );
    }

    @Test
    @DisplayName("入力値が不正な場合はServiceを呼ばず再設定画面に戻る")
    void resetPassword_InvalidInput_ShouldReturnResetView()
            throws Exception {

        mockMvc.perform(post("/reset-password")
                .param("email", "invalid-email")
                .param("newPassword", "short")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/reset-password"))
                .andExpect(
                    model().attributeHasFieldErrors(
                        "resetPasswordForm",
                        "email",
                        "newPassword"
                    )
                );

        verify(
        userDetailsService,
        never()
        ).updatePassword(
          anyString(),
          anyString()
       );
    }
}