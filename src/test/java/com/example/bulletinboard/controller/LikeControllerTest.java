package com.example.bulletinboard.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.bulletinboard.model.Post;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.service.CustomUserDetailsService;
import com.example.bulletinboard.service.LikeService;
import com.example.bulletinboard.service.PostService;

/**
 * 【クラスの役割】
 * LikeController（いいね非同期API）のWebレイヤー結合テストクラス。
 * 擬似的なHTTP POSTリクエスト（/posts/{postId}/like）を発行し、
 * 非同期レスポンス（200 OK + JSON）、未ログイン時の制御、CSRFトークン検証をテストする。
 */
@WebMvcTest(LikeController.class)
public class LikeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LikeService likeService;

    @MockitoBean
    private PostService postService;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("非同期通信: ログインユーザーがPOSTを送ると、200 OKとJSONデータが返ること")
    void toggleLike_Async_AuthenticatedUser_ShouldReturnJson() throws Exception {
        // Given: モックデータと動作の事前定義
        User mockUser = new User();
        mockUser.setUsername("testuser");

        Post mockPost = new Post();
        mockPost.setId(1L);

        when(userDetailsService.findByUsername("testuser")).thenReturn(Optional.of(mockUser));
        when(postService.findById(1L)).thenReturn(Optional.of(mockPost));
        when(likeService.toggleLike(mockUser, mockPost)).thenReturn(true);
        when(likeService.getLikeCount(mockPost)).thenReturn(1L); // ※カウント取得メソッドがある場合

        // When & Then: 非同期リクエストの送信とJSONレスポンスの検証
        mockMvc.perform(post("/posts/1/like")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.count").value(1));

        // Serviceの処理が呼び出されたことを検証
        verify(likeService, times(1)).toggleLike(mockUser, mockPost);
    }

    @Test
    @DisplayName("非同期通信: 未ログイン状態でリクエストを送ると、ログイン画面へリダイレクトされること")
    void toggleLike_UnauthenticatedUser_ShouldRedirectToLogin() throws Exception {
        // When & Then: 認証情報なしでのPOSTリクエスト
        mockMvc.perform(post("/posts/1/like")
                .with(csrf()))
                .andExpect(status().isUnauthorized());//401 Unauthorized が返る挙動

        // 未認証のため、Serviceのロジックは一切実行されないこと
        verify(likeService, never()).toggleLike(any(), any());
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("セキュリティ: CSRFトークンがないPOSTリクエストは403 Forbiddenで遮断されること")
    void toggleLike_WithoutCsrf_ShouldReturnForbidden() throws Exception {
        // When & Then: csrf() なしでのPOSTリクエスト
        mockMvc.perform(post("/posts/1/like"))
                .andExpect(status().isForbidden());

        // CSRFエラーのため、Serviceのロジックは実行されないこと
        verify(likeService, never()).toggleLike(any(), any());
    }
}