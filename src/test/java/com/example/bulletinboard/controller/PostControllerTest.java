package com.example.bulletinboard.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.times;
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

    /**
     * 【追加】投稿削除処理のテスト
     * 投稿主または管理者が投稿削除リクエストを送った際、削除処理が実行されて投稿一覧画面へリダイレクトされることを検証します。
     */
    @Test
    @DisplayName("投稿削除時、削除処理が行われ投稿一覧画面にリダイレクトされること")
    @WithMockUser(username = "testuser01")
    void deletePost_success() throws Exception {
        // 1. [準備] 削除対象の投稿とユーザーデータの設定
        User postUser = new User();
        postUser.setUsername("testuser01");

        Post mockPost = new Post();
        mockPost.setId(1L);
        mockPost.setUser(postUser);

        when(postService.findById(1L)).thenReturn(Optional.of(mockPost));

        // 2. [実行 & 3. 検証] 投稿削除用URL（※実際のControllerのURL構造に合わせて調整してください）へPOSTリクエスト
        mockMvc.perform(post("/posts/1/delete")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/posts"));

        // サービス層の削除処理（deletePostまたはdeleteById）が1回呼び出されたことを検証
        verify(postService, times(1)).deleteById(1L);
    }
}