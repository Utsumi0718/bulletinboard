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

import com.example.bulletinboard.model.Answer;
import com.example.bulletinboard.model.Topic;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.service.AnswerService;
import com.example.bulletinboard.service.CustomUserDetailsService;
import com.example.bulletinboard.service.TopicService;

/*
 * 【クラスの役割】
 * AnswerControllerにおける回答の投稿・削除処理と、
 * 回答者本人または管理者による削除権限を検証するWebレイヤーテストです。
 *
 * 旧Comment / Post仕様のテストを、
 * Answer / Topic仕様へ移行しています。
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
    @WithMockUser(username = "testuser01")
    void testAddAnswer() throws Exception {

        User mockUser = new User();
        mockUser.setUsername("testuser01");

        Topic mockTopic = new Topic();
        mockTopic.setId(1L);

        when(userDetailsService.findByUsername("testuser01"))
                .thenReturn(Optional.of(mockUser));

        when(topicService.findById(1L))
                .thenReturn(Optional.of(mockTopic));

        mockMvc.perform(post("/comments/add")
                .param("topicId", "1")
                .param("content", "テスト回答です")
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/posts/1"));

        verify(answerService, times(1))
                .saveAnswer(any());
    }

    @Test
    @DisplayName("回答削除時、自分が投稿した回答であれば削除処理が行われTopic詳細画面へリダイレクトされる")
    @WithMockUser(username = "testuser01")
    void testDeleteAnswer() throws Exception {

        User answerUser = new User();
        answerUser.setUsername("testuser01");

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
                .deleteAnswer(1L);
    }

    /**
     * 【管理者による回答削除】
     * 回答者本人でなくても、
     * ROLE_ADMINを持つユーザーなら回答を削除できることを検証します。
     */
    @Test
    @DisplayName("回答削除時、ADMIN権限を持つユーザーであれば他人の回答でも削除できること")
    @WithMockUser(username = "adminUser", roles = "ADMIN")
    void testDeleteAnswer_ByAdmin() throws Exception {

        User otherUser = new User();
        otherUser.setUsername("violatingUser");

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
                .deleteAnswer(2L);
    }
}