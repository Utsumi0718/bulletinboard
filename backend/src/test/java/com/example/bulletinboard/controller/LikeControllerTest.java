package com.example.bulletinboard.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.bulletinboard.dto.like.LikeResponse;
import com.example.bulletinboard.service.LikeService;

/**
 * 【クラスの役割】
 * LikeControllerのWebレイヤーを検証するテストクラスです。
 *
 * 現段階では、LikeServiceの新しいメソッド仕様へ追従し、
 * LikeControllerが正常にコンパイル・動作できることを確認します。
 *
 * 【現在の主な検証内容】
 * - 認証済みユーザーがLike操作を実行できること
 * - answerIdとloginEmailがLikeServiceへ渡されること
 * - LikeResponseのlikedとlikeCountがJSONとして返ること
 * - 正常時に200 OKとなること
 * - 未ログイン時にLike処理が実行されないこと
 * - CSRFトークンがない場合にLike処理が実行されないこと
 *
 * ※ 正式なREST API仕様に合わせたController Testの拡充は、
 *    H. Like Controller Testで行います。
 */
@WebMvcTest(LikeController.class)
public class LikeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LikeService likeService;

    @Test
    @WithMockUser(username = "testuser@example.com")
    @DisplayName("ログインユーザーがLike操作を行うと200 OKとLikeResponseが返ること")
    void toggleLike_AuthenticatedUser_ShouldReturnJson() throws Exception {

        // Given
        LikeResponse response =
                new LikeResponse(
                        true,
                        1L
                );

        when(
                likeService.toggleLike(
                        1L,
                        "testuser@example.com"
                )
        ).thenReturn(response);

        // When & Then
        mockMvc.perform(
                post("/answers/1/like")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(csrf())
        )
                .andExpect(status().isOk())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(
                        jsonPath("$.liked").value(true)
                )
                .andExpect(
                        jsonPath("$.likeCount").value(1)
                );

        verify(
                likeService,
                times(1)
        ).toggleLike(
                1L,
                "testuser@example.com"
        );
    }

    @Test
    @DisplayName("未ログイン状態ではLike処理が実行されないこと")
    void toggleLike_UnauthenticatedUser_ShouldReturnUnauthorized()
            throws Exception {

        mockMvc.perform(
                post("/answers/1/like")
                        .with(csrf())
        )
                .andExpect(status().isUnauthorized());

        verify(
                likeService,
                never()
        ).toggleLike(
                anyLong(),
                anyString()
        );
    }

    @Test
    @WithMockUser(username = "testuser@example.com")
    @DisplayName("CSRFトークンがないPOSTリクエストは403 Forbiddenになること")
    void toggleLike_WithoutCsrf_ShouldReturnForbidden()
            throws Exception {

        mockMvc.perform(
                post("/answers/1/like")
        )
                .andExpect(status().isForbidden());

        verify(
                likeService,
                never()
        ).toggleLike(
                anyLong(),
                anyString()
        );
    }
}