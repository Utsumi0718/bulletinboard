package com.example.bulletinboard.controller;

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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.bulletinboard.service.CustomUserDetailsService;

/**
 * 【クラス全体の役割】
 * AuthControllerの新規ユーザー登録処理を検証するテストクラスです。
 *
 * MockMvcを使用してHTTPリクエストを疑似的に送信し、
 *
 * ・ユーザー名の重複チェック
 * ・メールアドレスの重複チェック
 * ・正常登録時の画面遷移
 *
 * が正しく動作することを確認します。
 *
 * CustomUserDetailsServiceはMockitoでモック化し、
 * Controllerの処理に焦点を当てて検証します。
 *
 * Spring SecurityのCSRF対策を通過させるため、
 * POSTリクエストには.with(csrf())を付与します。
 */
@SpringBootTest
@AutoConfigureMockMvc
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @Test
    @DisplayName("登録済みのユーザー名で新規登録すると、username重複エラーで登録画面に戻る")
    void register_DuplicateUsername_ShouldReturnRegisterViewWithErrors()
            throws Exception {

        when(userDetailsService.existsByUsername("tsubasa01"))
                .thenReturn(true);

        when(userDetailsService.existsByEmail("tsubasa01@example.com"))
                .thenReturn(false);

        mockMvc.perform(post("/register")
                .param("username", "tsubasa01")
                .param("email", "tsubasa01@example.com")
                .param("password", "password123")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(
                    model().attributeHasFieldErrorCode(
                        "registerForm",
                        "username",
                        "duplicate"
                    )
                );
    }

    @Test
    @DisplayName("登録済みのメールアドレスで新規登録すると、email重複エラーで登録画面に戻る")
    void register_DuplicateEmail_ShouldReturnRegisterViewWithErrors()
            throws Exception {

        when(userDetailsService.existsByUsername("newuser01"))
                .thenReturn(false);

        when(userDetailsService.existsByEmail("registered@example.com"))
                .thenReturn(true);

        mockMvc.perform(post("/register")
                .param("username", "newuser01")
                .param("email", "registered@example.com")
                .param("password", "password123")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(
                    model().attributeHasFieldErrorCode(
                        "registerForm",
                        "email",
                        "duplicate"
                    )
                );
    }

    @Test
    @DisplayName("未登録のユーザー名とメールアドレスで登録すると、ログイン画面へリダイレクトされる")
    void register_NewUser_ShouldRedirectToLogin()
            throws Exception {

        when(userDetailsService.existsByUsername("newuser01"))
                .thenReturn(false);

        when(userDetailsService.existsByEmail("newuser01@example.com"))
                .thenReturn(false);

        mockMvc.perform(post("/register")
                .param("username", "newuser01")
                .param("email", "newuser01@example.com")
                .param("password", "password123")
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(
                    redirectedUrl("/login?register_success")
                );
    }
}