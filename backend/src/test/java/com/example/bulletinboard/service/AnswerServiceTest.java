package com.example.bulletinboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.bulletinboard.model.Answer;
import com.example.bulletinboard.repository.AnswerRepository;

/*
 * 【クラスの役割】
 * AnswerServiceのビジネスロジックを検証する
 * Service層の単体テストクラスです。
 *
 * AnswerRepositoryをMockitoでモック化し、
 * 実際のDBを使用せずService層の処理を確認します。
 *
 * 【主な検証内容】
 * - Answerの保存
 * - Topicに紐づく回答一覧の取得
 * - IDによる回答取得
 * - Answerの論理削除
 */
@ExtendWith(MockitoExtension.class)
class AnswerServiceTest {

    @Mock
    private AnswerRepository answerRepository;

    @InjectMocks
    private AnswerService answerService;

    @Test
    @DisplayName("回答を保存できること")
    void saveAnswer_ShouldSaveAnswer() {

        Answer answer = new Answer();
        answer.setContent("テスト回答");

        when(
            answerRepository.save(answer)
        ).thenReturn(answer);

        Answer saved =
            answerService.saveAnswer(answer);

        assertThat(saved).isSameAs(answer);

        verify(
            answerRepository,
            times(1)
        ).save(answer);
    }

    @Test
    @DisplayName("指定したTopicに紐づく回答一覧を取得できること")
    void getAnswersByTopicId_ShouldReturnAnswers() {

        Answer answer1 = new Answer();
        answer1.setContent("回答1");

        Answer answer2 = new Answer();
        answer2.setContent("回答2");

        when(
            answerRepository
                .findByTopicIdAndDeletedAtIsNull(1L)
        ).thenReturn(
            List.of(answer1, answer2)
        );

        List<Answer> answers =
            answerService.getAnswersByTopicId(1L);

        assertThat(answers).hasSize(2);

        verify(
            answerRepository,
            times(1)
        ).findByTopicIdAndDeletedAtIsNull(1L);
    }

    @Test
    @DisplayName("削除されていない回答をIDで取得できること")
    void getAnswerById_ShouldReturnAnswer() {

        Answer answer = new Answer();
        answer.setId(1L);

        when(
            answerRepository
                .findByIdAndDeletedAtIsNull(1L)
        ).thenReturn(Optional.of(answer));

        Optional<Answer> result =
            answerService.getAnswerById(1L);

        assertThat(result).isPresent();

        assertThat(result.get())
            .isSameAs(answer);
    }

    @Test
    @DisplayName("回答を論理削除するとdeletedAtが設定されること")
    void deleteAnswer_ShouldSetDeletedAt() {

        Answer answer = new Answer();
        answer.setId(1L);

        when(
            answerRepository
                .findByIdAndDeletedAtIsNull(1L)
        ).thenReturn(Optional.of(answer));

        when(
            answerRepository.save(any(Answer.class))
        ).thenAnswer(
            invocation -> invocation.getArgument(0)
        );

        answerService.deleteAnswer(1L);

        assertThat(answer.getDeletedAt())
            .isNotNull();

        verify(
            answerRepository,
            times(1)
        ).save(answer);
    }
}