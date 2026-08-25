package com.example.bulletinboard.controller;

import static org.mockito.ArgumentMatchers.any;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.bulletinboard.model.Comment;
import com.example.bulletinboard.model.Post;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.service.CommentService;
import com.example.bulletinboard.service.CustomUserDetailsService;
import com.example.bulletinboard.service.PostService;

/*
 * 【クラスの役割】
 * CommentController におけるコメントの追加・削除処理および権限検証をテストするクラスです。
 */
@WebMvcTest(CommentController.class)
public class CommentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommentService commentService;

    @MockitoBean
    private PostService postService;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @Test
    @DisplayName("コメント投稿時、ログイン中のユーザーが自動的にCommentへセットされて保存される")
    @WithMockUser(username = "testuser01")
    void testAddComment() throws Exception {
       User mockUser = new User();
       mockUser.setUsername("testuser01");

       Post mockPost = new Post();
       mockPost.setId(1L);

       when(userDetailsService.findByUsername("testuser01")).thenReturn(Optional.of(mockUser));
       when(postService.findById(1L)).thenReturn(Optional.of(mockPost));

       mockMvc.perform(post("/comments/add")
                .param("postId", "1")
                .param("content", "テストコメントです")
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/posts/1"));

        verify(commentService, times(1)).saveComment(any());
    }

    @Test
    @DisplayName("コメント削除時、自分が投稿したコメントであれば削除処理が行われ、投稿詳細画面にリダイレクトされる")
    @WithMockUser(username = "testuser01")
    void testDeleteComment() throws Exception {
        User commentUser = new User();
        commentUser.setUsername("testuser01");

        Comment mockComment = new Comment();
        mockComment.setId(1L);
        mockComment.setUser(commentUser);

        when(commentService.getCommentById(1L)).thenReturn(Optional.of(mockComment));

        mockMvc.perform(post("/comments/1/delete")
                .param("postId", "10")
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/posts/10"));

        verify(commentService, times(1)).deleteComment(1L);
    }

    /**
     * 【追加】管理者権限（ADMIN）による他人のコメント削除のテスト
     * コメントの投稿主でなくても、ADMIN権限を持つユーザーであれば削除処理が正常に実行されることを検証します。
     */
    @Test
    @DisplayName("コメント削除時、ADMIN権限を持つユーザーであれば他人のコメントでも削除できること")
    @WithMockUser(username = "adminUser", roles = "ADMIN")
    void testDeleteComment_ByAdmin() throws Exception {
        // 一般ユーザー（違反ユーザー等）が作成したコメントデータ
        User otherUser = new User();
        otherUser.setUsername("violatingUser");

        Comment mockComment = new Comment();
        mockComment.setId(2L);
        mockComment.setUser(otherUser);

        when(commentService.getCommentById(2L)).thenReturn(Optional.of(mockComment));

        mockMvc.perform(post("/comments/2/delete")
                .param("postId", "10")
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/posts/10"));

        verify(commentService, times(1)).deleteComment(2L);
    }
}