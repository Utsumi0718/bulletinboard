package com.example.bulletinboard.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.bulletinboard.model.Answer;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.service.AnswerService;
import com.example.bulletinboard.service.CustomUserDetailsService;
import com.example.bulletinboard.service.LikeService;

/**
 * 【クラスの役割】
 * LikeController（いいね非同期API）のWebレイヤーテストクラスです。
 *
 * 擬似的なHTTP POSTリクエスト
 * （/answers/{answerId}/like）を発行し、
 * 回答へのいいね処理とJSONレスポンス、
 * 未ログイン時の制御、
 * CSRFトークン検証を確認します。
 *
 * 【主な検証内容】
 * - ログインユーザーが回答へいいねできること
 * - LikeService.toggleLike()が実行されること
 * - LikeService.getLikeCount()で最新のいいね件数を取得すること
 * - いいね状態をlikedとしてJSONへ返すこと
 * - いいね件数をcountとしてJSONへ返すこと
 * - 正常時に200 OKを返すこと
 * - 未ログイン時に401 Unauthorizedとなること
 * - CSRFトークンがない場合に403 Forbiddenとなること
 * - 認証・CSRFエラー時にいいね処理を実行しないこと
 *
 * 【設計上のポイント】
 * - 旧Postへのいいね処理をAnswerへのいいね仕様へ移行しています。
 * - ログインユーザーはPrincipalのemailを基準に取得します。
 * - 対象AnswerはAnswerServiceから取得します。
 * - 自分自身の回答へのいいね禁止や、
 *   論理削除済みAnswerへのいいね禁止などの詳細ルールは、
 *   feature/like-featureで追加実装・テストします。
 */
@WebMvcTest(LikeController.class)
public class LikeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LikeService likeService;

    // 変更：PostServiceではなくAnswerServiceをモック化
    @MockitoBean
    private AnswerService answerService;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @Test
    @WithMockUser(username = "testuser@example.com")
    @DisplayName("非同期通信: ログインユーザーが回答へいいねすると、200 OKとJSONデータが返ること")
    void toggleLike_Async_AuthenticatedUser_ShouldReturnJson() throws Exception {

        // Given
        User mockUser = new User();
        mockUser.setUsername("testuser");
        mockUser.setEmail("testuser@example.com");

        // 変更：PostではなくAnswerを用意
        Answer mockAnswer = new Answer();
        mockAnswer.setId(1L);

       when(userDetailsService.findByEmail("testuser@example.com"))
        .thenReturn(Optional.of(mockUser));

        when(answerService.getAnswerById(1L))
                .thenReturn(Optional.of(mockAnswer));

        when(likeService.toggleLike(mockUser, mockAnswer))
                .thenReturn(true);

        when(likeService.getLikeCount(mockAnswer))
                .thenReturn(1L);

        // When & Then
        mockMvc.perform(post("/answers/1/like")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.count").value(1));

        verify(likeService, times(1))
                .toggleLike(mockUser, mockAnswer);

        verify(likeService,times(1)).getLikeCount(mockAnswer);
    }

    @Test
    @DisplayName("非同期通信: 未ログイン状態でリクエストを送ると401 Unauthorizedになること")
    void toggleLike_UnauthenticatedUser_ShouldReturnUnauthorized()
            throws Exception {

        mockMvc.perform(post("/answers/1/like")
                .with(csrf()))
                .andExpect(status().isUnauthorized());

        verify(likeService, never())
                .toggleLike(any(), any());
    }

    @Test
    @WithMockUser(username = "testuser@example.com")
    @DisplayName("セキュリティ: CSRFトークンがないPOSTリクエストは403 Forbiddenになること")
    void toggleLike_WithoutCsrf_ShouldReturnForbidden()
            throws Exception {

        mockMvc.perform(post("/answers/1/like"))
                .andExpect(status().isForbidden());

        verify(likeService, never())
                .toggleLike(any(), any());
    }
}