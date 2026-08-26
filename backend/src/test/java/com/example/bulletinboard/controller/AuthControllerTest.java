package com.example.bulletinboard.controller;

import com.example.bulletinboard.service.CustomUserDetailsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * [クラスの役割]
 *  AuthControllerの動作（画面遷移や入力値バリテーション）を検証するためのテストクラス
 * MockMvcを使用して実際のHTTPリクエストを疑似的に送信して、
 *ユーザー新規登録時の重複チェックエラー処理や、正常登録時のリダイレクト処理が正しく機能するかを自動テストします。
 * ‐@MockBeanを利用してCustomUserDetailsService をモック化し、データベース環境に依存せずコントローラー単体の挙動を高速に検証します。
 * -Spring Security が要求する CSRF 対策を通過させるため、リクエスト送信時に `.with(csrf())` を付与しています
 */

@SpringBootTest
@AutoConfigureMockMvc
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc; //HTTPを疑似的に送信するためのもの

    @MockitoBean
    private CustomUserDetailsService userDetailsService;//サービス層をモック化

    @Test
    @DisplayName("登録済みのユーザー名で新規登録を試みた結果、登録重複エラーが発生して登録画面に戻る")
    void test_register_DuplicateUsername_ShouldReturnRegisterViewWithErrors() throws Exception {
        //初期値の設定（"tsubasa01"は登録ユーザーとして存在する）
        when(userDetailsService.existsByUsername("tsubasa01")).thenReturn(true);

        //テストの処理と検証
        mockMvc.perform(post("/register")
               .param("username","tsubasa01")
               .param("password","password123")
               .with(csrf()))//セキュリティ制御をパスするためCSRFトークンを模擬付与
               .andExpect(status().isOk())//HTTP 200 OK（画面再表示）であること
               .andExpect(view().name("auth/register"))//登録画面が表示されること
               .andExpect(model().attributeHasFieldErrorCode("registerForm", "username", "duplicate")); // 重複エラーコードが検出されること
    }

    @Test
    @DisplayName("新規ユーザーで登録した場合、ログイン画面にリダイレクトされる")
    void register_NewUsername_ShouldRedirectToLogin() throws Exception {
        //初期設定（newuserは未登録のユーザーとして定義）
        when(userDetailsService.existsByUsername("newuser01")).thenReturn(false);

        //テストの処理と検証
         mockMvc.perform(post("/register")
               .param("username","newuser01")
               .param("password","password123")
               .with(csrf()))//セキュリティ制御をパスするためCSRFトークンを模擬付与
               .andExpect(status().is3xxRedirection())//リダイレクトレスポンスであること
               .andExpect(redirectedUrl("/login?register_success")); //成功パラメーター付きでログイン画面へ遷移すること

    }
}
