package com.example.bulletinboard.controller;

import com.example.bulletinboard.model.Post;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.service.CustomUserDetailsService;
import com.example.bulletinboard.service.PostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean; // ★こちらに変更
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostController.class)
public class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean // ★ @MockBean から変更
    private PostService postService;

    @MockitoBean // ★ @MockBean から変更
    private CustomUserDetailsService userDetailsService;

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