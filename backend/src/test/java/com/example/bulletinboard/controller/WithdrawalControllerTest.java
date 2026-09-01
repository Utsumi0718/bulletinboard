package com.example.bulletinboard.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.bulletinboard.repository.UserRepository;
import com.example.bulletinboard.security.SecurityConfig;
import com.example.bulletinboard.service.CustomUserDetailsService;

/**
 * 【クラス全体の役割】
 * WithdrawalControllerのWeb層における
 * ユーザー退会処理を検証するテストクラスです。
 *
 * 【主な検証内容】
 * - 認証中ユーザーのemailを基準に退会処理が実行されること
 * - 退会成功時にログイン画面へリダイレクトされること
 * - 退会処理に失敗した場合にエラー付きURLへ遷移すること
 * - ROLE_ADMINは退会処理を実行できないこと
 *
 * 【テスト方針】
 * CustomUserDetailsServiceはMockitoでモック化し、
 * ControllerからServiceへ正しいemailが渡されることと、
 * 退会処理結果に応じた画面遷移を検証します。
 *
 * accountStatusやwithdrawnAtの更新そのものは
 * CustomUserDetailsServiceTestで検証します。
 */

@WebMvcTest(WithdrawalController.class)
@Import(SecurityConfig.class)
class WithdrawalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    /*
     * SecurityConfigが依存しているためモック化します。
     */
    @MockitoBean
    private UserRepository userRepository;

        @Test
    @WithMockUser(
        username = "test@example.com",
        roles = "USER"
    )
    @DisplayName("退会に成功するとログアウトされログイン画面へリダイレクトされる")
    void withdraw_WhenSuccessful_ShouldLogoutAndRedirectToLogin()
            throws Exception {

        when(userDetailsService.withdrawUser("test@example.com"))
                .thenReturn(true);

        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(
                post("/account/withdraw")
                    .session(session)
                    .with(csrf())
        )
        .andExpect(status().is3xxRedirection())
        .andExpect(
                redirectedUrl("/login?withdraw_success")
        );

        verify(userDetailsService)
                .withdrawUser("test@example.com");

        /*
         * SecurityContextLogoutHandlerによって
         * HTTPセッションが無効化されたことを確認します。
         */
        assertTrue(session.isInvalid());
    }

    @Test
    @WithMockUser(
        username = "test@example.com",
        roles = "USER"
    )
    @DisplayName("退会処理に失敗すると退会エラーURLへリダイレクトされる")
    void withdraw_WhenFailed_ShouldRedirectToError()
            throws Exception {

        when(userDetailsService.withdrawUser("test@example.com"))
                .thenReturn(false);

        mockMvc.perform(
                post("/account/withdraw")
                    .with(csrf())
        )
        .andExpect(status().is3xxRedirection())
        .andExpect(
                redirectedUrl("/account/withdraw?error")
        );

        verify(userDetailsService)
                .withdrawUser("test@example.com");
    }


    @Test
    @DisplayName("未認証ユーザーは退会処理を実行できない")
    void withdraw_WhenUnauthenticated_ShouldNotBeAccessible()
            throws Exception {

        mockMvc.perform(
                post("/account/withdraw")
                    .with(csrf())
        )
        .andExpect(status().is3xxRedirection());

        verify(
                userDetailsService,
                never()
        ).withdrawUser(anyString());
    }

    @Test
    @WithMockUser(
        username = "admin@example.com",
        roles = "ADMIN"
    )
    @DisplayName("ROLE_ADMINは退会処理を実行できない")
    void withdraw_WhenAdmin_ShouldBeForbidden()
            throws Exception {

        mockMvc.perform(
                post("/account/withdraw")
                    .with(csrf())
        )
        .andExpect(status().isForbidden());

        verify(
                userDetailsService,
                never()
        ).withdrawUser(anyString());
    }
}