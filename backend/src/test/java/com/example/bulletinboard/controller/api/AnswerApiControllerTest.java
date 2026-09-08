package com.example.bulletinboard.controller.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.bulletinboard.model.Answer;
import com.example.bulletinboard.model.Topic;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.service.AnswerService;
import com.example.bulletinboard.service.TopicService;


/*
 * 【クラスの役割】
 * AnswerApiControllerのREST API動作を検証する
 * Webレイヤーテストです。
 *
 * MockMvcを使用してHTTPリクエストを送信し、
 * HTTP StatusやJSONレスポンス、
 * AnswerService・TopicServiceの呼び出しを確認します。
 *
 * 【現在の検証対象】
 * - GET /api/topics/{topicId}/answers
 *   → 指定TopicのAnswer一覧取得
 *   → 正常時 200 OK
 *   → AnswerResponseの内容確認
 *   → Answerが0件の場合は空配列
 *   → Topic不存在時は404 Not Found
 *
 * 【今後の検証対象】
 * - POST /api/topics/{topicId}/answers
 * - PUT /api/answers/{id}
 * - DELETE /api/answers/{id}
 *
 * 【設計上のポイント】
 * - 旧AnswerControllerTestのredirect検証はREST APIでは行いません。
 * - REST APIではHTTP StatusとJSONレスポンスを検証します。
 * - Topicの存在確認はTopicServiceへ委譲します。
 * - Answer一覧取得はAnswerServiceへ委譲します。
 */
@WebMvcTest(AnswerApiController.class)
class AnswerApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnswerService answerService;

    @MockitoBean
    private TopicService topicService;


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
}