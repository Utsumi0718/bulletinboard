package com.example.bulletinboard.controller.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.bulletinboard.exception.ForbiddenOperationException;
import com.example.bulletinboard.exception.TopicEditConflictException;
import com.example.bulletinboard.exception.TopicNotFoundException;
import com.example.bulletinboard.model.Topic;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.service.CustomUserDetailsService;
import com.example.bulletinboard.service.TopicService;


/**
 * 【クラスの役割】
 * TopicApiControllerのREST API動作を検証するテストクラスです。
 *
 * MockMvcを使ってHTTPリクエストを送信し、
 * HTTP StatusやJSONレスポンスの内容、
 * TopicServiceの呼び出しを確認します。
 *
 * 【現在の検証内容】
 * - Topic通常一覧取得
 * - keywordによるTopic.title部分一致検索
 * - 空keyword時の400 Bad Request
 * - 検索結果0件時のレスポンス
 * - Topic詳細取得
 * - Topic不存在時の404 Not Found
 * - Topic新規投稿
 *   → 201 Created
 *   → Locationヘッダー
 *   → TopicResponse
 *   → 認証ユーザーの紐付け確認
 * - Topic新規投稿時のUser取得失敗
 *   → 500 Internal Server Error
 *   → ErrorResponse確認
 * - Topic新規投稿時のValidationエラー
 *   → 400 Bad Request
 *   → ValidationErrorResponse確認
 * - Topic編集
 *   → 200 OK
 *   → TopicResponse
 *   → loginEmailの受け渡し確認
 *   → title / image / questionの受け渡し確認
 * - Topic編集時のValidationエラー
 *   → 400 Bad Request
 *   → ValidationErrorResponse確認
 * - 投稿者本人以外によるTopic編集
 *   → 403 Forbidden
 *   → ErrorResponse確認
 *
 * - Answerが存在するTopic編集
 *   → 409 Conflict
 *   → ErrorResponse確認
 *
 * - Topic編集対象が存在しない場合
 *   → 404 Not Found
 *   → ErrorResponse確認
 */

@WebMvcTest(TopicApiController.class)
class TopicApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TopicService topicService;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @Test
    @DisplayName("keyword未指定の場合はTopic一覧を200 OKで取得できること")
    @WithMockUser(username = "testuser01@example.com")
    void getTopics_WithoutKeyword_ShouldReturnTopicList() throws Exception {

        Topic topic = new Topic();
        topic.setId(1L);
        topic.setTitle("猫のお題");
        topic.setImage("/images/cat.jpg");
        topic.setCreatedAt(LocalDateTime.of(2026, 9, 7, 10, 0));

        Page<Topic> topicPage =
                new PageImpl<>(
                        List.of(topic),
                        org.springframework.data.domain.PageRequest.of(0, 10),
                        1
                );

        when(
                topicService.findAll(
                        0,
                        "createdAt",
                        "desc"
                )
        ).thenReturn(topicPage);

        mockMvc.perform(
                get("/api/topics")
                        .param("page", "0")
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].title").value("猫のお題"))
                .andExpect(jsonPath("$.content[0].image").value("/images/cat.jpg"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        verify(topicService).findAll(
                0,
                "createdAt",
                "desc"
        );
    }

    @Test
    @DisplayName("keyword指定時はTopic.titleの部分一致検索が実行されること")
    @WithMockUser(username = "testuser01@example.com")
    void getTopics_WithKeyword_ShouldSearchTopics() throws Exception {

        Topic topic = new Topic();
        topic.setId(1L);
        topic.setTitle("猫のお題");
        topic.setImage("/images/cat.jpg");
        topic.setCreatedAt(LocalDateTime.of(2026, 9, 7, 10, 0));

        Page<Topic> topicPage =
                new PageImpl<>(
                        List.of(topic),
                        org.springframework.data.domain.PageRequest.of(0, 10),
                        1
                );

        when(
                topicService.searchTopics(
                        0,
                        "猫",
                        "createdAt",
                        "desc"
                )
        ).thenReturn(topicPage);

        mockMvc.perform(
                get("/api/topics")
                        .param("keyword", "猫")
                        .param("page", "0")
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("猫のお題"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10));

        verify(topicService).searchTopics(
                0,
                "猫",
                "createdAt",
                "desc"
        );
    }

    @Test
    @DisplayName("keywordが空文字の場合は400 Bad Requestになること")
    @WithMockUser(username = "testuser01@example.com")
    void getTopics_BlankKeyword_ShouldReturnBadRequest() throws Exception {

        mockMvc.perform(
                get("/api/topics")
                        .param("keyword", "")
                        .param("page", "0")
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message")
                        .value("検索ワードを入力してください"))
                .andExpect(jsonPath("$.path").value("/api/topics"));
    }

    @Test
    @DisplayName("検索結果が0件の場合は200 OKで空のcontentを返すこと")
    @WithMockUser(username = "testuser01@example.com")
    void getTopics_NoSearchResults_ShouldReturnEmptyPage() throws Exception {

        Page<Topic> emptyPage =
                new PageImpl<>(
                        List.of(),
                        org.springframework.data.domain.PageRequest.of(0, 10),
                        0
                );

        when(
                topicService.searchTopics(
                        0,
                        "存在しない文字列",
                        "createdAt",
                        "desc"
                )
        ).thenReturn(emptyPage);

        mockMvc.perform(
                get("/api/topics")
                        .param("keyword", "存在しない文字列")
                        .param("page", "0")
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));

        verify(topicService).searchTopics(
                0,
                "存在しない文字列",
                "createdAt",
                "desc"
        );
    }

    @Test
    @DisplayName("Topic詳細を200 OKで取得できること")
    @WithMockUser(username = "testuser01@example.com")
    void getTopic_ShouldReturnTopicDetail() throws Exception {

    User user = new User();
    user.setUsername("testuser01");

    Topic topic = new Topic();
    topic.setId(1L);
    topic.setTitle("猫のお題");
    topic.setImage("/images/cat.jpg");
    topic.setQuestion("この猫、何を考えてる？");
    topic.setUser(user);
    topic.setCreatedAt(LocalDateTime.of(2026, 9, 7, 10, 0));
    topic.setUpdatedAt(LocalDateTime.of(2026, 9, 7, 11, 0));

    when(
            topicService.getById(1L)
    ).thenReturn(topic);

    mockMvc.perform(
            get("/api/topics/1")
    )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.title").value("猫のお題"))
            .andExpect(jsonPath("$.image").value("/images/cat.jpg"))
            .andExpect(jsonPath("$.question")
                    .value("この猫、何を考えてる？"))
            .andExpect(jsonPath("$.username").value("testuser01"));

    verify(topicService).getById(1L);
}

    @Test
    @DisplayName("存在しないTopicを取得すると404 Not Foundになること")
    @WithMockUser(username = "testuser01@example.com")
    void getTopic_TopicNotFound_ShouldReturnNotFound() throws Exception {

    when(
            topicService.getById(999L)
    ).thenThrow(
            new TopicNotFoundException(
                    "このお題は存在しないか、削除されています。"
            )
    );

    mockMvc.perform(
            get("/api/topics/999")
    )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.error").value("Not Found"))
            .andExpect(jsonPath("$.message")
                    .value("このお題は存在しないか、削除されています。"))
            .andExpect(jsonPath("$.path")
                    .value("/api/topics/999"));

    verify(topicService).getById(999L);
}

@Test
@DisplayName("Topicを新規投稿すると201 Createdと作成済みTopicを返すこと")
@WithMockUser(username = "testuser01@example.com")
void createTopic_ShouldReturnCreatedTopic() throws Exception {

    User user = new User();
    user.setUsername("testuser01");
    user.setEmail("testuser01@example.com");

    when(
            userDetailsService.findByEmail(
                    "testuser01@example.com"
            )
    ).thenReturn(Optional.of(user));

    when(
            topicService.save(any(Topic.class))
    ).thenAnswer(invocation -> {

        Topic topic = invocation.getArgument(0);

        topic.setId(123L);
        topic.setCreatedAt(
                LocalDateTime.of(2026, 9, 7, 13, 0)
        );
        topic.setUpdatedAt(
                LocalDateTime.of(2026, 9, 7, 13, 0)
        );

        return topic;
    });

    mockMvc.perform(
            post("/api/topics")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "title": "猫のお題",
                              "image": "/images/cat.jpg",
                              "question": "この猫、何を考えてる？"
                            }
                            """)
    )
            .andExpect(status().isCreated())
            .andExpect(header().string(
                    "Location",
                    "/api/topics/123"
            ))
            .andExpect(jsonPath("$.id").value(123))
            .andExpect(jsonPath("$.title").value("猫のお題"))
            .andExpect(jsonPath("$.image").value("/images/cat.jpg"))
            .andExpect(jsonPath("$.question")
                    .value("この猫、何を考えてる？"))
            .andExpect(jsonPath("$.username")
                    .value("testuser01"));

    verify(userDetailsService).findByEmail(
            "testuser01@example.com"
    );

    ArgumentCaptor<Topic> topicCaptor =
            ArgumentCaptor.forClass(Topic.class);

    verify(topicService).save(topicCaptor.capture());

    Topic savedTopic = topicCaptor.getValue();

    assertThat(savedTopic.getTitle())
            .isEqualTo("猫のお題");

    assertThat(savedTopic.getImage())
            .isEqualTo("/images/cat.jpg");

    assertThat(savedTopic.getQuestion())
            .isEqualTo("この猫、何を考えてる？");

    assertThat(savedTopic.getUser())
            .isSameAs(user);
}


@Test
@DisplayName("認証ユーザー情報を取得できない場合は500 Internal Server Errorになること")
@WithMockUser(username = "testuser01@example.com")
void createTopic_UserNotFound_ShouldReturnInternalServerError() throws Exception {

    when(
            userDetailsService.findByEmail(
                    "testuser01@example.com"
            )
    ).thenReturn(Optional.empty());

    mockMvc.perform(
            post("/api/topics")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "title": "猫のお題",
                              "image": "/images/cat.jpg",
                              "question": "この猫、何を考えてる？"
                            }
                            """)
    )
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.error")
                    .value("Internal Server Error"))
            .andExpect(jsonPath("$.message")
                    .value("ログインユーザー情報を取得できませんでした。"))
            .andExpect(jsonPath("$.path")
                    .value("/api/topics"));

    verify(userDetailsService).findByEmail(
            "testuser01@example.com"
    );
}

@Test
@DisplayName("Topic新規投稿でValidationエラーの場合は400 Bad Requestになること")
@WithMockUser(username = "testuser01@example.com")
void createTopic_ValidationError_ShouldReturnBadRequest() throws Exception {

    mockMvc.perform(
            post("/api/topics")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "title": "",
                              "image": "/images/cat.jpg",
                              "question": "この猫、何を考えてる？"
                            }
                            """)
    )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error")
                    .value("Bad Request"))
            .andExpect(jsonPath("$.message")
                    .value("入力内容に誤りがあります。"))
            .andExpect(jsonPath("$.path")
                    .value("/api/topics"))
            .andExpect(jsonPath("$.fieldErrors.title").exists());
}

@Test
@DisplayName("Topicを編集すると200 OKと更新後Topicを返すこと")
@WithMockUser(username = "owner@example.com")
void updateTopic_ShouldReturnUpdatedTopic() throws Exception {

    User user = new User();
    user.setUsername("owner");
    user.setEmail("owner@example.com");

    Topic updatedTopic = new Topic();
    updatedTopic.setId(1L);
    updatedTopic.setTitle("変更後タイトル");
    updatedTopic.setImage("/images/after.jpg");
    updatedTopic.setQuestion("変更後の問題");
    updatedTopic.setUser(user);
    updatedTopic.setCreatedAt(
            LocalDateTime.of(2026, 9, 7, 10, 0)
    );
    updatedTopic.setUpdatedAt(
            LocalDateTime.of(2026, 9, 7, 12, 0)
    );

    when(
            topicService.updateTopic(
                    1L,
                    "owner@example.com",
                    "変更後タイトル",
                    "/images/after.jpg",
                    "変更後の問題"
            )
    ).thenReturn(updatedTopic);

    mockMvc.perform(
            put("/api/topics/1")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "title": "変更後タイトル",
                              "image": "/images/after.jpg",
                              "question": "変更後の問題"
                            }
                            """)
    )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.title")
                    .value("変更後タイトル"))
            .andExpect(jsonPath("$.image")
                    .value("/images/after.jpg"))
            .andExpect(jsonPath("$.question")
                    .value("変更後の問題"))
            .andExpect(jsonPath("$.username")
                    .value("owner"));

    verify(topicService).updateTopic(
            1L,
            "owner@example.com",
            "変更後タイトル",
            "/images/after.jpg",
            "変更後の問題"
    );
}

@Test
@DisplayName("Topic編集でValidationエラーの場合は400 Bad Requestになること")
@WithMockUser(username = "owner@example.com")
void updateTopic_ValidationError_ShouldReturnBadRequest() throws Exception {

    mockMvc.perform(
            put("/api/topics/1")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "title": "",
                              "image": "/images/after.jpg",
                              "question": "変更後の問題"
                            }
                            """)
    )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error")
                    .value("Bad Request"))
            .andExpect(jsonPath("$.message")
                    .value("入力内容に誤りがあります。"))
            .andExpect(jsonPath("$.path")
                    .value("/api/topics/1"))
            .andExpect(jsonPath("$.fieldErrors.title").exists());
}

@Test
@DisplayName("投稿者本人以外がTopicを編集しようとすると403 Forbiddenになること")
@WithMockUser(username = "other@example.com")
void updateTopic_NotOwner_ShouldReturnForbidden() throws Exception {

    when(
            topicService.updateTopic(
                    1L,
                    "other@example.com",
                    "変更後タイトル",
                    "/images/after.jpg",
                    "変更後の問題"
            )
    ).thenThrow(
            new ForbiddenOperationException(
                    "このお題を編集する権限がありません。"
            )
    );

    mockMvc.perform(
            put("/api/topics/1")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "title": "変更後タイトル",
                              "image": "/images/after.jpg",
                              "question": "変更後の問題"
                            }
                            """)
    )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.error").value("Forbidden"))
            .andExpect(jsonPath("$.message")
                    .value("このお題を編集する権限がありません。"))
            .andExpect(jsonPath("$.path")
                    .value("/api/topics/1"));

    verify(topicService).updateTopic(
            1L,
            "other@example.com",
            "変更後タイトル",
            "/images/after.jpg",
            "変更後の問題"
    );
}

@Test
@DisplayName("回答が存在するTopicを編集しようとすると409 Conflictになること")
@WithMockUser(username = "owner@example.com")
void updateTopic_AnswerExists_ShouldReturnConflict() throws Exception {

    when(
            topicService.updateTopic(
                    1L,
                    "owner@example.com",
                    "変更後タイトル",
                    "/images/after.jpg",
                    "変更後の問題"
            )
    ).thenThrow(
            new TopicEditConflictException(
                    "回答が投稿されたお題は編集できません。"
            )
    );

    mockMvc.perform(
            put("/api/topics/1")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "title": "変更後タイトル",
                              "image": "/images/after.jpg",
                              "question": "変更後の問題"
                            }
                            """)
    )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.error").value("Conflict"))
            .andExpect(jsonPath("$.message")
                    .value("回答が投稿されたお題は編集できません。"))
            .andExpect(jsonPath("$.path")
                    .value("/api/topics/1"));

    verify(topicService).updateTopic(
            1L,
            "owner@example.com",
            "変更後タイトル",
            "/images/after.jpg",
            "変更後の問題"
    );
}

@Test
@DisplayName("存在しないTopicを編集しようとすると404 Not Foundになること")
@WithMockUser(username = "owner@example.com")
void updateTopic_TopicNotFound_ShouldReturnNotFound() throws Exception {

    when(
            topicService.updateTopic(
                    999L,
                    "owner@example.com",
                    "変更後タイトル",
                    "/images/after.jpg",
                    "変更後の問題"
            )
    ).thenThrow(
            new TopicNotFoundException(
                    "このお題は存在しないか、削除されています。"
            )
    );

    mockMvc.perform(
            put("/api/topics/999")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "title": "変更後タイトル",
                              "image": "/images/after.jpg",
                              "question": "変更後の問題"
                            }
                            """)
    )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.error").value("Not Found"))
            .andExpect(jsonPath("$.message")
                    .value("このお題は存在しないか、削除されています。"))
            .andExpect(jsonPath("$.path")
                    .value("/api/topics/999"));

    verify(topicService).updateTopic(
            999L,
            "owner@example.com",
            "変更後タイトル",
            "/images/after.jpg",
            "変更後の問題"
    );
}
}