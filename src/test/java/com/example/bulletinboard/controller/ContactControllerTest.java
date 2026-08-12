package com.example.bulletinboard.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/*
 * 【クラスの役割】
 * ContactController における画面遷移（入力・確認・完了）、入力バリデーション、
 * およびメール送信処理（JavaMailSender）の呼び出しを自動検証する単体テストクラスです。
 */
@WebMvcTest(ContactController.class)
@TestPropertySource(properties = "spring.mail.username=test@example.com")
class ContactControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JavaMailSender mailSender;

    @Test
    @WithMockUser
    @DisplayName("GET /contact : 入力画面が正常に表示されること")
    void index_success() throws Exception {
        mockMvc.perform(get("/contact")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("contact/index"))
                .andExpect(model().attributeExists("contactForm"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /contact/confirm : 正常な入力値の場合、確認画面が表示されること")
    void confirm_success() throws Exception {
        mockMvc.perform(post("/contact/confirm")
                        .with(csrf())
                        .param("name", "テスト太郎")
                        .param("email", "test@example.com")
                        .param("subject", "テスト件名")
                        .param("message", "テスト本文です。"))
                .andExpect(status().isOk())
                .andExpect(view().name("contact/confirm"))
                .andExpect(model().hasNoErrors());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /contact/confirm : 入力値エラーの場合、入力画面に戻りエラーが保持されること")
    void confirm_validationError() throws Exception {
        mockMvc.perform(post("/contact/confirm")
                        .with(csrf())
                        .param("name", "") // 名前を空にする
                        .param("email", "invalid-email") // 不正なメール形式
                        .param("subject", "")
                        .param("message", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("contact/index"))
                .andExpect(model().hasErrors());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /contact/send : 送信処理が正常に呼び出され、完了画面が表示されること")
    void send_success() throws Exception {
        mockMvc.perform(post("/contact/send")
                        .with(csrf())
                        .param("name", "テスト太郎")
                        .param("email", "user@example.com")
                        .param("subject", "テスト件名")
                        .param("message", "テスト本文です。"))
                .andExpect(status().isOk())
                .andExpect(view().name("contact/complete"));

        // メール送信メソッド（send）が1回呼び出されたことを検証
        verify(mailSender).send(any(SimpleMailMessage.class));
    }
}