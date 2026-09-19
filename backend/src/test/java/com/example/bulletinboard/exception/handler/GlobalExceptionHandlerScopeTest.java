package com.example.bulletinboard.exception.handler;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.ServletException;

/**
 * 【クラスの役割】
 * GlobalExceptionHandlerのControllerへの適用範囲を検証します。
 *
 * 同じ例外を発生させるテスト用Controllerを用意し、
 * RestControllerでは共通JSONへ変換され、
 * 通常のControllerではこのHandlerが処理しないことを確認します。
 *
 * Security・DB・Thymeleafの画面描画は対象外です。
 * MVC側の最終的なエラー画面やHTTPステータスは検証しません。
 */
class GlobalExceptionHandlerScopeTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new TestApiController(),
                        new TestMvcController()
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("RestControllerの例外には共通JSON処理を適用する")
    void restController_shouldUseGlobalExceptionHandler()
            throws Exception {

        mockMvc.perform(get("/test-scope/api"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message")
                        .value("適用範囲の確認用エラー"))
                .andExpect(jsonPath("$.path")
                        .value("/test-scope/api"));
    }

    @Test
    @DisplayName("通常のControllerの例外には共通JSON処理を適用しない")
    void mvcController_shouldNotUseGlobalExceptionHandler() {

        // このテストではMVC用の例外処理を登録していないため、
        // 未処理の例外がMockMvcの呼び出し元へ伝わることを確認します。
        assertThatThrownBy(
                () -> mockMvc.perform(get("/test-scope/mvc"))
        )
                .isInstanceOf(ServletException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("適用範囲の確認用エラー");
    }

    @RestController
    public static class TestApiController {

        @GetMapping("/test-scope/api")
        public String fail() {
            throw new IllegalArgumentException("適用範囲の確認用エラー");
        }
    }

    @Controller
    public static class TestMvcController {

        @GetMapping("/test-scope/mvc")
        public String fail() {
            throw new IllegalArgumentException("適用範囲の確認用エラー");
        }
    }
}