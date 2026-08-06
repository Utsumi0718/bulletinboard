package com.example.bulletinboard.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.bulletinboard.model.Post;
import com.example.bulletinboard.model.Comment;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.service.CommentService;
import com.example.bulletinboard.service.PostService;
import com.example.bulletinboard.service.CustomUserDetailsService;

@WebMvcTest(CommentController.class)//CommentControllerのユニットテスト
public class CommentControllerTest {

    @Autowired
    private MockMvc mockMvc; // 疑似的なHTTPリクエストを送信するためのオブジェクト

    @MockitoBean
    private CommentService commentService; // CommentServiceのモックオブジェクトをDIコンテナに登録


    @MockitoBean
    private PostService postService;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @Test
    @DisplayName("コメント投稿時、ログイン中のユーザーが自動的にCommentへセットされて保存される")
    @WithMockUser(username = "testuser01") // 疑似的にログインユーザーを設定
    void testAddComment() throws Exception {

       //事前準備（Mockの挙動を設定）
       User mockUser = new User();
       mockUser.setUsername("testuser01");

       Post mockPost = new Post();
       mockPost.setId(1L);

       when(userDetailsService.findByUsername("testuser01")).thenReturn(Optional.of(mockUser));
       when(postService.findById(1L)).thenReturn(Optional.of(mockPost));

      //test対象のURLにPOSTリクエストを送信
      mockMvc.perform(post("/comments/add")
                .param("postId", "1")
                .param("content", "テストコメントです")
                .with(csrf())) // CSRFトークンを自動で含める [2]
                // 3. 検証
                .andExpect(status().is3xxRedirection()) // 302リダイレクトが発生するか [1]
                .andExpect(redirectedUrl("/posts/1")); // 投稿詳細画面にリダイレクトされるか [1]

        // サービスが1回呼び出されたことを確認
        verify(commentService, times(1)).saveComment(any());//URLを検証
    }

    @Test
    @DisplayName("コメント削除時、自分が投稿したコメントであれば削除処理が行われ、投稿詳細画面にリダイレクトされる")
    @WithMockUser(username = "testuser01") // 疑似的にログインユーザー（testuser01）を設定
    void testDeleteComment() throws Exception {

        // 1. 事前準備（Mockの挙動を設定）
        // コメントを書いた本人のユーザーデータを準備
        User commentUser = new User();
        commentUser.setUsername("testuser01");

        // 削除対象のコメントデータを準備し、上記のユーザーをセット
        Comment mockComment = new Comment();
        mockComment.setId(1L);
        mockComment.setUser(commentUser);

        // コントローラーの getCommentById(1L) が呼ばれたら、準備したコメントデータを返す設定
        when(commentService.getCommentById(1L)).thenReturn(Optional.of(mockComment));

        // 2. テスト対象のURLにPOSTリクエストを送信
        // URLの {id} に「1」、RequestParam の postId に「10」を指定（リダイレクト先の確認用）
        mockMvc.perform(post("/comments/1/delete")
                .param("postId", "10")
                .with(csrf())) // CSRFトークンを自動で含める [2]
                // 3. 検証
                .andExpect(status().is3xxRedirection()) // 302リダイレクトが発生するか [1]
                .andExpect(redirectedUrl("/posts/10")); // 正しいpostIdの詳細画面にリダイレクトされるか [1]

        // サービス層の削除メソッドが、指定したコメントID「1L」で確かに1回呼び出されたことを確認
        verify(commentService, times(1)).deleteComment(1L);
    }



}
