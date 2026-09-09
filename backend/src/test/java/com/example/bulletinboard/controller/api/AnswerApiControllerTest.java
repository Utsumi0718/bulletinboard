package com.example.bulletinboard.controller.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.bulletinboard.exception.AnswerEditConflictException;
import com.example.bulletinboard.exception.AnswerNotFoundException;
import com.example.bulletinboard.exception.ForbiddenOperationException;
import com.example.bulletinboard.exception.TopicNotFoundException;
import com.example.bulletinboard.model.Answer;
import com.example.bulletinboard.model.Topic;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.service.AnswerService;
import com.example.bulletinboard.service.CustomUserDetailsService;
import com.example.bulletinboard.service.TopicService;

/*
 * 【クラスの役割】
 * AnswerApiControllerのREST API動作を検証する
 * Webレイヤーテストです。
 *
 * MockMvcを使用してHTTPリクエストを送信し、
 * HTTP StatusやJSONレスポンス、
 * AnswerService・TopicService・CustomUserDetailsServiceの
 * 呼び出しを確認します。
 *
 * 【現在の検証対象】
 * - GET /api/topics/{topicId}/answers
 *   → 指定TopicのAnswer一覧取得
 *   → 正常時 200 OK
 *   → AnswerResponseの内容確認
 *   → Answerが0件の場合は空配列
 *   → Topic不存在時は404 Not Found
 *
 * - POST /api/topics/{topicId}/answers
 *   → 指定TopicへのAnswer投稿
 *   → 正常時 201 Created
 *   → loginEmailからUser取得
 *   → Topic取得
 *   → AnswerへUser / Topic / contentを設定
 *   → Validationエラー時 400 Bad Request
 *   → Topic不存在時 404 Not Found
 *   → User取得失敗時 500 Internal Server Error
 *
 * - PUT /api/answers/{id}
 *   → Answerの編集
 *   → AuthenticationからloginEmailを取得
 *   → AnswerRequestのcontentをAnswerServiceへ渡す
 *   → 正常時 200 OK
 *   → AnswerResponseの内容確認
 *   → Validationエラー時 400 Bad Request
 *   → 投稿者本人以外の編集時 403 Forbidden
 *   → Answer不存在時 404 Not Found
 *   → Likeが付いているAnswerの編集時 409 Conflict
 *
 * 【今後の検証対象】
 * - DELETE /api/answers/{id}
 *
 * 【設計上のポイント】
 * - 旧AnswerControllerTestのredirect検証はREST APIでは行いません。
 * - REST APIではHTTP StatusとJSONレスポンスを検証します。
 * - 認証PrincipalにはログインIDであるemailが設定されます。
 * - Topicの存在確認はTopicServiceへ委譲します。
 * - Answer一覧取得・保存・編集はAnswerServiceへ委譲します。
 * - ログインユーザー取得はCustomUserDetailsServiceへ委譲します。
 * - Answer編集時の業務ルール判定はAnswerServiceへ委譲します。
 * - GlobalExceptionHandlerを通して、
 *   Validationエラー・権限エラー・不存在・編集競合を
 *   REST API用のエラーレスポンスへ変換します。
 */

@WebMvcTest(AnswerApiController.class)
class AnswerApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnswerService answerService;

    @MockitoBean
    private TopicService topicService;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;


@Test
@DisplayName("指定TopicのAnswer一覧を200 OKで取得できること")
@WithMockUser(username = "testuser01@example.com")
void getAnswersByTopicId_ShouldReturnAnswerList() throws Exception {

    Topic topic = new Topic();
    topic.setId(1L);

    User user = new User();
    user.setUsername("testuser01");
    user.setEmail("testuser01@example.com");

    Answer answer = new Answer();
    answer.setId(10L);
    answer.setTopic(topic);
    answer.setUser(user);
    answer.setContent("テスト回答です");
    answer.setCreatedAt(
            LocalDateTime.of(2026, 9, 8, 20, 0)
    );
    answer.setUpdatedAt(
            LocalDateTime.of(2026, 9, 8, 20, 0)
    );

    when(
            topicService.getById(1L)
    ).thenReturn(topic);

    when(
            answerService.getAnswersByTopicId(1L)
    ).thenReturn(List.of(answer));

    mockMvc.perform(
            get("/api/topics/1/answers")
    )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(10))
            .andExpect(jsonPath("$[0].content")
                    .value("テスト回答です"))
            .andExpect(jsonPath("$[0].username")
                    .value("testuser01"));

    verify(topicService).getById(1L);

    verify(answerService)
            .getAnswersByTopicId(1L);
}

@Test
@DisplayName("指定TopicにAnswerが0件の場合は200 OKで空配列を返すこと")
@WithMockUser(username = "testuser01@example.com")
void getAnswersByTopicId_NoAnswers_ShouldReturnEmptyList() throws Exception {

    Topic topic = new Topic();
    topic.setId(1L);

    when(
            topicService.getById(1L)
    ).thenReturn(topic);

    when(
            answerService.getAnswersByTopicId(1L)
    ).thenReturn(List.of());

    mockMvc.perform(
            get("/api/topics/1/answers")
    )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());

    verify(topicService).getById(1L);

    verify(answerService)
            .getAnswersByTopicId(1L);
}

@Test
@DisplayName("存在しないTopicのAnswer一覧を取得しようとすると404 Not Foundになること")
@WithMockUser(username = "testuser01@example.com")
void getAnswersByTopicId_TopicNotFound_ShouldReturnNotFound() throws Exception {

    when(
            topicService.getById(999L)
    ).thenThrow(
            new TopicNotFoundException(
                    "このお題は存在しないか、削除されています。"
            )
    );

    mockMvc.perform(
            get("/api/topics/999/answers")
    )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.error").value("Not Found"))
            .andExpect(jsonPath("$.message")
                    .value("このお題は存在しないか、削除されています。"))
            .andExpect(jsonPath("$.path")
                    .value("/api/topics/999/answers"));

   verify(answerService, never())
        .getAnswersByTopicId(999L);
}

@Test
@DisplayName("指定TopicへAnswerを投稿すると201 Createdと作成済みAnswerを返すこと")
@WithMockUser(username = "testuser01@example.com")
void createAnswer_ShouldReturnCreatedAnswer() throws Exception {

    User user = new User();
    user.setUsername("testuser01");
    user.setEmail("testuser01@example.com");

    Topic topic = new Topic();
    topic.setId(1L);

    when(
            userDetailsService.findByEmail(
                    "testuser01@example.com"
            )
    ).thenReturn(Optional.of(user));

    when(
            topicService.getById(1L)
    ).thenReturn(topic);

    when(
            answerService.saveAnswer(any(Answer.class))
    ).thenAnswer(invocation -> {

        Answer answer = invocation.getArgument(0);

        answer.setId(100L);
        answer.setCreatedAt(
                LocalDateTime.of(2026, 9, 9, 8, 0)
        );
        answer.setUpdatedAt(
                LocalDateTime.of(2026, 9, 9, 8, 0)
        );

        return answer;
    });

    mockMvc.perform(
            post("/api/topics/1/answers")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "content": "テスト回答です"
                            }
                            """)
    )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(100))
            .andExpect(jsonPath("$.content")
                    .value("テスト回答です"))
            .andExpect(jsonPath("$.username")
                    .value("testuser01"));

    verify(userDetailsService)
            .findByEmail("testuser01@example.com");

    verify(topicService)
            .getById(1L);

    ArgumentCaptor<Answer> answerCaptor =
            ArgumentCaptor.forClass(Answer.class);

    verify(answerService)
            .saveAnswer(answerCaptor.capture());

    Answer savedAnswer =
            answerCaptor.getValue();

    assertThat(savedAnswer.getTopic())
            .isSameAs(topic);

    assertThat(savedAnswer.getUser())
            .isSameAs(user);

    assertThat(savedAnswer.getContent())
            .isEqualTo("テスト回答です");
}

@Test
@DisplayName("Answer投稿でValidationエラーの場合は400 Bad Requestになること")
@WithMockUser(username = "testuser01@example.com")
void createAnswer_ValidationError_ShouldReturnBadRequest() throws Exception {

    mockMvc.perform(
            post("/api/topics/1/answers")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "content": ""
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
                    .value("/api/topics/1/answers"))
            .andExpect(jsonPath("$.fieldErrors.content").exists());
}

@Test
@DisplayName("存在しないTopicへAnswerを投稿しようとすると404 Not Foundになること")
@WithMockUser(username = "testuser01@example.com")
void createAnswer_TopicNotFound_ShouldReturnNotFound() throws Exception {

    User user = new User();
    user.setUsername("testuser01");
    user.setEmail("testuser01@example.com");

    when(
            userDetailsService.findByEmail(
                    "testuser01@example.com"
            )
    ).thenReturn(Optional.of(user));

    when(
            topicService.getById(999L)
    ).thenThrow(
            new TopicNotFoundException(
                    "このお題は存在しないか、削除されています。"
            )
    );

    mockMvc.perform(
            post("/api/topics/999/answers")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "content": "テスト回答です"
                            }
                            """)
    )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.error").value("Not Found"))
            .andExpect(jsonPath("$.message")
                    .value("このお題は存在しないか、削除されています。"))
            .andExpect(jsonPath("$.path")
                    .value("/api/topics/999/answers"));

    verify(userDetailsService)
            .findByEmail("testuser01@example.com");

    verify(topicService)
            .getById(999L);
}

@Test
@DisplayName("Answer投稿時にログインユーザー情報を取得できない場合は500 Internal Server Errorになること")
@WithMockUser(username = "testuser01@example.com")
void createAnswer_UserNotFound_ShouldReturnInternalServerError() throws Exception {

    when(
            userDetailsService.findByEmail(
                    "testuser01@example.com"
            )
    ).thenReturn(Optional.empty());

    mockMvc.perform(
            post("/api/topics/1/answers")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "content": "テスト回答です"
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
                    .value("/api/topics/1/answers"));

    verify(userDetailsService)
            .findByEmail("testuser01@example.com");
}


@Test
@DisplayName("投稿者本人がAnswerを編集すると200 OKと更新済みAnswerを返すこと")
@WithMockUser(username = "testuser01@example.com")
void updateAnswer_ShouldReturnUpdatedAnswer() throws Exception {

    User user = new User();
    user.setUsername("testuser01");
    user.setEmail("testuser01@example.com");

    Answer updatedAnswer = new Answer();
    updatedAnswer.setId(100L);
    updatedAnswer.setUser(user);
    updatedAnswer.setContent("変更後の回答");
    updatedAnswer.setCreatedAt(
            LocalDateTime.of(2026, 9, 9, 8, 0)
    );
    updatedAnswer.setUpdatedAt(
            LocalDateTime.of(2026, 9, 9, 9, 0)
    );

    when(
            answerService.updateAnswer(
                    100L,
                    "testuser01@example.com",
                    "変更後の回答"
            )
    ).thenReturn(updatedAnswer);

    mockMvc.perform(
            put("/api/answers/100")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "content": "変更後の回答"
                            }
                            """)
    )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(100))
            .andExpect(jsonPath("$.content")
                    .value("変更後の回答"))
            .andExpect(jsonPath("$.username")
                    .value("testuser01"));

    verify(answerService)
            .updateAnswer(
                    100L,
                    "testuser01@example.com",
                    "変更後の回答"
            );
}

@Test
@DisplayName("Answer編集でValidationエラーの場合は400 Bad Requestになること")
@WithMockUser(username = "testuser01@example.com")
void updateAnswer_ValidationError_ShouldReturnBadRequest() throws Exception {

    mockMvc.perform(
            put("/api/answers/100")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "content": ""
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
                    .value("/api/answers/100"))
            .andExpect(jsonPath("$.fieldErrors.content").exists());

    verify(
            answerService,
            never()
    ).updateAnswer(
            any(Long.class),
            any(String.class),
            any(String.class)
    );
}

@Test
@DisplayName("投稿者本人以外がAnswerを編集しようとすると403 Forbiddenになること")
@WithMockUser(username = "other@example.com")
void updateAnswer_Forbidden_ShouldReturnForbidden() throws Exception {

    when(
            answerService.updateAnswer(
                    100L,
                    "other@example.com",
                    "変更後の回答"
            )
    ).thenThrow(
            new ForbiddenOperationException(
                    "この回答を編集する権限がありません。"
            )
    );

    mockMvc.perform(
            put("/api/answers/100")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "content": "変更後の回答"
                            }
                            """)
    )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.error").value("Forbidden"))
            .andExpect(jsonPath("$.message")
                    .value("この回答を編集する権限がありません。"))
            .andExpect(jsonPath("$.path")
                    .value("/api/answers/100"));

    verify(answerService)
            .updateAnswer(
                    100L,
                    "other@example.com",
                    "変更後の回答"
            );
}


@Test
@DisplayName("存在しないAnswerを編集しようとすると404 Not Foundになること")
@WithMockUser(username = "testuser01@example.com")
void updateAnswer_AnswerNotFound_ShouldReturnNotFound() throws Exception {

    when(
            answerService.updateAnswer(
                    999L,
                    "testuser01@example.com",
                    "変更後の回答"
            )
    ).thenThrow(
            new AnswerNotFoundException(
                    "この回答は存在しないか、削除されています。"
            )
    );

    mockMvc.perform(
            put("/api/answers/999")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "content": "変更後の回答"
                            }
                            """)
    )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.error").value("Not Found"))
            .andExpect(jsonPath("$.message")
                    .value("この回答は存在しないか、削除されています。"))
            .andExpect(jsonPath("$.path")
                    .value("/api/answers/999"));
}

@Test
@DisplayName("Likeが付いているAnswerを編集しようとすると409 Conflictになること")
@WithMockUser(username = "testuser01@example.com")
void updateAnswer_LikeExists_ShouldReturnConflict() throws Exception {

    when(
            answerService.updateAnswer(
                    100L,
                    "testuser01@example.com",
                    "変更後の回答"
            )
    ).thenThrow(
            new AnswerEditConflictException(
                    "いいねが付いている回答は編集できません。"
            )
    );

    mockMvc.perform(
            put("/api/answers/100")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "content": "変更後の回答"
                            }
                            """)
    )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.error").value("Conflict"))
            .andExpect(jsonPath("$.message")
                    .value("いいねが付いている回答は編集できません。"))
            .andExpect(jsonPath("$.path")
                    .value("/api/answers/100"));
}

}