package com.example.bulletinboard.controller;

import com.example.bulletinboard.model.Comment;
import com.example.bulletinboard.model.Post;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.service.CommentService;
import com.example.bulletinboard.service.CustomUserDetailsService;
import com.example.bulletinboard.service.LikeService;
import com.example.bulletinboard.service.PostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/*
 * 【クラスの役割】
 * コントローラー（PostController, CommentController）におけるフラッシュメッセージ（RedirectAttributes）の動作を検証する単体テストクラスです。
 * 他人の投稿・コメントに対する操作時のエラーメッセージ設定や、自コメント削除時の成功メッセージ設定が、
 * リダイレクト時に正しいフラッシュスコープ（Flash Scope）へ保持されるかを MockMvc を用いて自動検証します。
 */
@WebMvcTest({PostController.class, CommentController.class})
class FlashMessageTest {

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
    @WithMockUser(username = "user1")
    @DisplayName("投稿更新時：他人の投稿の場合はerrorMessageがフラッシュメッセージにセットされてリダイレクトすること")
    void updatePost_unauthorized_flashMessage() throws Exception {
        // 投稿者（ownerUser）と ログインユーザー（user1）が異なる状況を作成
        User owner = new User();
        owner.setUsername("ownerUser");

        Post existingPost = new Post();
        existingPost.setId(1L);
        existingPost.setUser(owner);

        given(postService.findById(1L)).willReturn(Optional.of(existingPost));

        // POSTリクエストを送信してレスポンス検証
        mockMvc.perform(post("/posts/1")
                        .with(csrf())
                        .param("title", "更新後のタイトル")
                        .param("content", "更新後の本文"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/posts"))
                .andExpect(flash().attribute("errorMessage", "投稿の更新に失敗しました。"));
    }

    @Test
    @WithMockUser(username = "user1")
    @DisplayName("コメント削除時：自分のコメントの場合はsuccessMessageがフラッシュメッセージにセットされてリダイレクトすること")
    void deleteComment_success_flashMessage() throws Exception {
        // ログインユーザーとコメント投稿者が一致する状況を作成
        User user = new User();
        user.setUsername("user1");

        Comment comment = new Comment();
        comment.setId(10L);
        comment.setUser(user);

        given(commentService.getCommentById(10L)).willReturn(Optional.of(comment));

        // POSTリクエストを送信してレスポンス検証
        mockMvc.perform(post("/comments/10/delete")
                        .with(csrf())
                        .param("postId", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/posts/1"))
                .andExpect(flash().attribute("successMessage", "user1さんのコメントが削除されました！"));
    }
}