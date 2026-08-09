package com.example.bulletinboard.controller;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
 * LikeController（いいねリクエストの受付・画面遷移）のWebレイヤー結合テストクラス。
 * 擬似的なHTTP POSTリクエスト（/posts/{postId}/like）を発行し、
 * ログイン状態での権限チェック、LikeServiceの呼び出し、Referer先へのリダイレクト動作を検証する。
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
    @DisplayName("ログインユーザーがPOSTリクエストを送ると、いいね処理が実行されReferer先へリダイレクトされること")
    void toggleLike_AuthenticatedUser_ShouldToggleAndRedirect() throws Exception {
        // Given: ユーザーと投稿のモックデータ設定
        User mockUser = new User();
        mockUser.setUsername("testuser");

        Post mockPost = new Post();
        mockPost.setId(1L);

        when(userDetailsService.findByUsername("testuser")).thenReturn(Optional.of(mockUser));
        when(postService.findById(1L)).thenReturn(Optional.of(mockPost));

        // When & Then: リクエスト送信とリダイレクト結果の検証
        mockMvc.perform(post("/posts/1/like")
                .header("Referer", "http://localhost:8080/posts/1")
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:8080/posts/1"));

        // Serviceの処理が呼び出されたことを検証
        verify(likeService, times(1)).toggleLike(mockUser, mockPost);
    }
}