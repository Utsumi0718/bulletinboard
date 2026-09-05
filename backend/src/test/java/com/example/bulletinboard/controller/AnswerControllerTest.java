package com.example.bulletinboard.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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

import com.example.bulletinboard.model.Answer;
import com.example.bulletinboard.model.Topic;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.service.AnswerService;
import com.example.bulletinboard.service.CustomUserDetailsService;
import com.example.bulletinboard.service.TopicService;

/*
 * 【クラスの役割】
 * AnswerControllerにおける回答の投稿・削除処理を検証する
 * Webレイヤーテストです。
 *
 * 【主な検証内容】
 * - 回答投稿時にログインユーザーをemailから取得できること
 * - 回答投稿時に対象Topicを取得できること
 * - Answerへログインユーザーが正しく設定されること
 * - Answerへ対象Topicが正しく設定されること
 * - Answerへ入力されたcontentが正しく設定されること
 * - 一般ユーザーによる回答削除時に
 *   loginEmailとisAdmin=falseがAnswerServiceへ渡されること
 * - ROLE_ADMINによる回答削除時に
 *   loginEmailとisAdmin=trueがAnswerServiceへ渡されること
 * - 投稿・削除後に対象Topic詳細画面へリダイレクトされること
 *
 * 【設計上のポイント】
 * - 認証PrincipalにはログインIDであるemailが設定されます。
 * - 回答投稿時はログインユーザーと対象Topicを取得し、
 *   Answerへ紐付けてAnswerServiceへ渡します。
 * - 回答削除の最終的な権限判定はAnswerService側で行います。
 * - 現在は旧Thymeleaf画面と旧URL構造を使用しています。
 * - Answer編集処理は現在のAnswerControllerには存在しないため、
 *   後続工程でREST API化とあわせて整理します。
 */




@WebMvcTest(AnswerController.class)
public class AnswerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnswerService answerService;

    @MockitoBean
    private TopicService topicService;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @Test
    @DisplayName("回答投稿時、ログイン中のユーザーと対象TopicがAnswerへセットされて保存される")
    @WithMockUser(username = "testuser01@example.com")
    void testAddAnswer() throws Exception {

        User mockUser = new User();
        mockUser.setUsername("testuser01");
        mockUser.setEmail("testuser01@example.com");

        Topic mockTopic = new Topic();
        mockTopic.setId(1L);

        when(userDetailsService.findByEmail("testuser01@example.com"))
                .thenReturn(Optional.of(mockUser));

        when(topicService.findById(1L))
                .thenReturn(Optional.of(mockTopic));

        mockMvc.perform(post("/comments/add")
                        .param("topicId", "1")
                        .param("content", "テスト回答です")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/posts/1"));

      ArgumentCaptor<Answer> answerCaptor =
        ArgumentCaptor.forClass(Answer.class);

      verify(
        answerService,
        times(1)
      ).saveAnswer(answerCaptor.capture());

     Answer savedAnswer =
        answerCaptor.getValue();

     assertSame(
         mockUser,
         savedAnswer.getUser()
      );

      assertSame(
        mockTopic,
        savedAnswer.getTopic()
       );

     assertEquals(
    "テスト回答です",
         savedAnswer.getContent()
    );
    }

    @Test
    @DisplayName("回答削除時、自分が投稿した回答であれば削除処理が行われTopic詳細画面へリダイレクトされる")
    @WithMockUser(username = "testuser01@example.com")
    void testDeleteAnswer() throws Exception {

        User answerUser = new User();
        answerUser.setUsername("testuser01");
        answerUser.setEmail("testuser01@example.com");

        Answer mockAnswer = new Answer();
        mockAnswer.setId(1L);
        mockAnswer.setUser(answerUser);

        when(answerService.getAnswerById(1L))
                .thenReturn(Optional.of(mockAnswer));

        mockMvc.perform(post("/comments/1/delete")
                        .param("topicId", "10")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/posts/10"));

        verify(answerService, times(1))
        .deleteAnswer(
                1L,
                "testuser01@example.com",
                false
        );
    }


    @Test
    @DisplayName("回答削除時、ADMIN権限を持つユーザーであれば他人の回答でも削除できること")
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void testDeleteAnswer_ByAdmin() throws Exception {

        User otherUser = new User();
        otherUser.setUsername("violatingUser");
        otherUser.setEmail("violating@example.com");

        Answer mockAnswer = new Answer();
        mockAnswer.setId(2L);
        mockAnswer.setUser(otherUser);

        when(answerService.getAnswerById(2L))
                .thenReturn(Optional.of(mockAnswer));

        mockMvc.perform(post("/comments/2/delete")
                        .param("topicId", "10")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/posts/10"));
       verify(answerService, times(1))
        .deleteAnswer(
                2L,
                "admin@example.com",
                true
        );
    }
}