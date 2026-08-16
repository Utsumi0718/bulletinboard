package com.example.bulletinboard.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.bulletinboard.model.Post;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.service.CommentService;
import com.example.bulletinboard.service.CustomUserDetailsService;
import com.example.bulletinboard.service.LikeService;
import com.example.bulletinboard.service.PostService;

/*
 * 【クラス全体の役割】
 * 投稿管理（PostController）のWebレイヤー（リクエスト制御、データバリデーション、画面遷移など）を検証する
 * コントローラー層専用の単体テストクラスです。
 *
 * 【設計・補足ポイント】
 * - `@WebMvcTest(PostController.class)` を使用して、テストに必要なWeb周辺のBeanのみを軽量に起動させています。
 * - フルスタックなSpring Security（SecurityConfig）をそのまま読み込ませると依存関係の連鎖で ApplicationContext の
 *   起動失敗を招くため、不要なインポートを排除し、MockMvcの自動セキュリティ機能に処理を委ねています。
 * - `PostController` がコンストラクタ経由で要求する4つのサービスコンポーネント（Post, UserDetails, Comment, Like）を、
 *   すべて `@MockitoBean` としてテストコンテキスト内に漏れなく定義し、インジェクション不足による起動例外を防止しています。
 * - ログイン中のコンテキスト状態をシミュレートするため、`@WithMockUser` アノテーションを用いて擬似的な認証情報を付与しています。
 */
@WebMvcTest(PostController.class)
public class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostService postService;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @MockitoBean
    private CommentService commentService;

    @MockitoBean
    private LikeService likeService;

    @Test
    @DisplayName("新規投稿時、ログイン中のユーザーが自動的にPostへセットされて保存されること")
    @WithMockUser(username = "testuser01")
    void createPost_ShouldAttachLoggedInUserToPost() throws Exception {
        // 1. [準備] モックユーザーの設定
        User mockUser = new User();
        mockUser.setUsername("testuser01");
        when(userDetailsService.findByUsername("testuser01")).thenReturn(Optional.of(mockUser));

        // 2. [実行] POST リクエストを送信
        mockMvc.perform(post("/posts")
                        .param("title", "自動紐づけテスト")
                        .param("content", "本文テキスト")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/posts"));

        // 3. [検証] postService.save(post) に渡された Post の中身を検証
        ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
        verify(postService).save(postCaptor.capture());

        Post savedPost = postCaptor.getValue();
        assertNotNull(savedPost.getUser(), "PostにUserがセットされていること");
        assertEquals("testuser01", savedPost.getUser().getUsername(), "セットされたUser名が一致すること");
    }
}
