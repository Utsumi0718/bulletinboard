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

import com.example.bulletinboard.model.Topic;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.service.CustomUserDetailsService;
import com.example.bulletinboard.service.TopicService;

/*
 * 【クラス全体の役割】
 * TopicControllerのWebレイヤーにおける
 * リクエスト制御・ユーザー紐付け・Serviceへの認証情報受け渡し・
 * 画面遷移を検証するテストです。
 *
 * 旧PostController / Post仕様のテストを、
 * TopicController / Topic仕様へ移行しています。
 *
 * 認証Principalにはemailが設定されるため、
 * 削除処理ではログインユーザーのemailとROLE_ADMIN権限の有無が
 * TopicServiceへ正しく渡されることを確認します。
 *
 * Topic削除の最終的な権限判定はTopicService側で行います。
 */
@WebMvcTest(TopicController.class)
public class TopicControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TopicService topicService;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @Test
    @DisplayName("新規お題投稿時、ログイン中のユーザーがTopicへセットされて保存されること")
    @WithMockUser(username = "testuser01@example.com")
    void createTopic_ShouldAttachLoggedInUserToTopic() throws Exception {

        // 1. モックユーザーの設定
        User mockUser = new User();
        mockUser.setUsername("testuser01");
        mockUser.setEmail("testuser01@example.com");

        when(userDetailsService.findByEmail("testuser01@example.com"))
                .thenReturn(Optional.of(mockUser));

        // 2. POSTリクエスト
        mockMvc.perform(post("/posts")
                        .param("title", "自動紐づけテスト")
                        .param("image", "/images/test.jpg")
                        .param("question", "テスト用のお題です")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/posts"));

        // 3. TopicService#saveへ渡されたTopicを検証
        ArgumentCaptor<Topic> topicCaptor =
                ArgumentCaptor.forClass(Topic.class);

        verify(topicService).save(topicCaptor.capture());

        Topic savedTopic = topicCaptor.getValue();

        assertNotNull(
                savedTopic.getUser(),
                "TopicにUserがセットされていること");

        assertEquals(
                "testuser01",
                savedTopic.getUser().getUsername(),
                "セットされたUser名が一致すること");

        assertEquals(
                "testuser01@example.com",
                savedTopic.getUser().getEmail(),
                "セットされたUserのemailが一致すること");
    }

  @Test
@DisplayName("お題削除時、ログインユーザーのemailと管理者権限をServiceへ渡して一覧画面へリダイレクトされること")
@WithMockUser(username = "testuser01@example.com")
void deleteTopic_success() throws Exception {

    mockMvc.perform(
            post("/posts/1/delete")
                .with(csrf())
        )
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/posts"));

    verify(
        topicService,
        times(1)
    ).deleteById(
        1L,
        "testuser01@example.com",
        false
    );
}

@Test
@DisplayName("管理者がお題を削除する場合は管理者権限trueをServiceへ渡すこと")
@WithMockUser(
    username = "admin@example.com",
    roles = "ADMIN"
)
void deleteTopic_Admin_ShouldPassAdminTrue() throws Exception {

    mockMvc.perform(
            post("/posts/1/delete")
                .with(csrf())
        )
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/posts"));

    verify(
        topicService,
        times(1)
    ).deleteById(
        1L,
        "admin@example.com",
        true
    );
}

@Test
@DisplayName("お題更新時に入力値とログインユーザーのemailをServiceへ渡すこと")
@WithMockUser(username = "testuser01@example.com")
void updateTopic_ShouldPassInputAndLoginEmailToService()
        throws Exception {

    mockMvc.perform(
            post("/posts/1")
                .param("title", "更新後タイトル")
                .param("image", "/images/updated.jpg")
                .param("question", "更新後のお題です")
                .with(csrf())
        )
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/posts"));

    verify(
        topicService,
        times(1)
    ).updateTopic(
        1L,
        "testuser01@example.com",
        "更新後タイトル",
        "/images/updated.jpg",
        "更新後のお題です"
    );
}
}