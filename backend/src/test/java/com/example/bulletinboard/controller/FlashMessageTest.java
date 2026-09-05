package com.example.bulletinboard.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
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
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.service.AnswerService;
import com.example.bulletinboard.service.CustomUserDetailsService;
import com.example.bulletinboard.service.TopicService;

/*
 * 【クラスの役割】
 * TopicController / AnswerControllerにおける
 * フラッシュメッセージ（RedirectAttributes）の動作を検証する
 * Webレイヤーテストです。
 *
 * 【主な検証内容】
 * - Topic更新成功時にsuccessMessageが
 *   Flash Scopeへ正しく設定されること
 * - Answer削除成功時にsuccessMessageが
 *   Flash Scopeへ正しく設定されること
 * - 処理完了後に正しい画面へリダイレクトされること
 *
 * 【設計上のポイント】
 * - Topic更新時の権限判定や編集可否判定は
 *   TopicService側へ委譲します。
 * - Answer削除時の権限判定は
 *   AnswerService側へ委譲します。
 * - Service側で発生した権限エラーや編集不可エラーを
 *   FlashMessageへ変換する処理は、
 *   STEP 12の旧Thymeleaf Controller整理時に対応します。
 * - 旧Post / Comment仕様のテストを、
 *   Topic / Answer仕様へ移行しています。
 */
@WebMvcTest({TopicController.class, AnswerController.class})
class FlashMessageTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TopicService topicService;

    @MockitoBean
    private AnswerService answerService;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

@Test
@WithMockUser(username = "user1@example.com")
@DisplayName("お題更新成功時にsuccessMessageがフラッシュメッセージにセットされてリダイレクトすること")
void updateTopic_success_flashMessage() throws Exception {

    mockMvc.perform(
            post("/posts/1")
                .with(csrf())
                .param("title", "更新後のタイトル")
                .param("image", "/images/updated.jpg")
                .param("question", "更新後のお題")
        )
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/posts"))
        .andExpect(
            flash().attribute(
                "successMessage",
                "投稿の更新に成功しました！"
            )
        );
}

    @Test
    @WithMockUser(username = "user1@example.com")
    @DisplayName("回答削除時：自分の回答の場合はsuccessMessageがフラッシュメッセージにセットされてリダイレクトすること")
    void deleteAnswer_success_flashMessage() throws Exception {

        // ログインユーザーと回答者が一致する状況を作成
        User user = new User();
        user.setUsername("user1");
        user.setEmail("user1@example.com");

        Answer answer = new Answer();
        answer.setId(10L);
        answer.setUser(user);

        given(answerService.getAnswerById(10L))
                .willReturn(Optional.of(answer));

        // POSTリクエストを送信してレスポンス検証
        mockMvc.perform(post("/comments/10/delete")
                        .with(csrf())
                        .param("topicId", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/posts/1"))
                .andExpect(
                        flash().attribute(
                                "successMessage",
                                "user1さんの回答が削除されました！"
                        )
                );
    }
}