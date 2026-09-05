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
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.AnswerRepository;

/*
 * 【クラスの役割】
 * AnswerServiceのビジネスロジックを検証する
 * Service層の単体テストクラスです。
 *
 * AnswerRepositoryとLikeServiceをMockitoでモック化し、
 * 実際のDBを使用せずService層の処理を確認します。
 *
 * 【主な検証内容】
 * - Answerの保存
 * - Topicに紐づく回答一覧の取得
 * - IDによる削除されていないAnswerの取得
 * - 投稿者本人によるAnswerの論理削除
 * - 投稿者本人かつLikeが0件の場合のAnswer編集
 * - 投稿者本人以外によるAnswer編集の拒否
 * - Likeが1件以上存在するAnswer編集の拒否
 *
 * 【設計上のポイント】
 * - Answer編集は投稿者本人のみ可能です。
 * - Likeが1件以上付いているAnswerは編集できません。
 * - Like件数の確認にはLikeService.getLikeCount()を使用します。
 * - Answer削除は物理削除ではなくdeletedAtを設定する論理削除です。
 * - Answer削除時のROLE_ADMINや権限なしのケースは
 *   後続のService Testで確認します。
 */

@ExtendWith(MockitoExtension.class)
class AnswerServiceTest {

    @Mock
    private AnswerRepository answerRepository;

    @Mock
    private LikeService likeService;

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
    @DisplayName("投稿者本人が回答を論理削除するとdeletedAtが設定されること")
    void deleteAnswer_ShouldSetDeletedAt() {

    User answerUser = new User();
    answerUser.setEmail("testuser01@example.com");

    Answer answer = new Answer();
    answer.setId(1L);
    answer.setUser(answerUser);

    when(
        answerRepository
            .findByIdAndDeletedAtIsNull(1L)
    ).thenReturn(Optional.of(answer));

    when(
        answerRepository.save(any(Answer.class))
    ).thenAnswer(
        invocation -> invocation.getArgument(0)
    );

    answerService.deleteAnswer(
        1L,
        "testuser01@example.com",
        false
    );

    assertThat(answer.getDeletedAt())
        .isNotNull();

    verify(
        answerRepository,
        times(1)
     ).save(answer);
  }

  @Test
  @DisplayName("投稿者本人かつLikeが0件なら回答を編集できること")
  void updateAnswer_OwnerAndNoLikes_ShouldUpdateAnswer() {

    User answerUser = new User();
    answerUser.setEmail("owner@example.com");

    Answer answer = new Answer();
    answer.setId(1L);
    answer.setUser(answerUser);
    answer.setContent("変更前");

    when(
        answerRepository.findByIdAndDeletedAtIsNull(1L)
    ).thenReturn(Optional.of(answer));

    when(
        likeService.getLikeCount(answer)
    ).thenReturn(0L);

    when(
        answerRepository.save(any(Answer.class))
    ).thenAnswer(
        invocation -> invocation.getArgument(0)
    );

    Answer updated = answerService.updateAnswer(
        1L,
        "owner@example.com",
        "変更後"
    );

    assertThat(updated.getContent())
        .isEqualTo("変更後");

    verify(
        answerRepository,
        times(1)
    ).save(answer);
}

@Test
@DisplayName("投稿者本人以外は回答を編集できないこと")
void updateAnswer_NotOwner_ShouldThrowException() {

    User answerUser = new User();
    answerUser.setEmail("owner@example.com");

    Answer answer = new Answer();
    answer.setId(1L);
    answer.setUser(answerUser);

    when(
        answerRepository.findByIdAndDeletedAtIsNull(1L)
    ).thenReturn(Optional.of(answer));

    org.assertj.core.api.Assertions
        .assertThatThrownBy(
            () -> answerService.updateAnswer(
                1L,
                "other@example.com",
                "変更後"
            )
        )
        .isInstanceOf(IllegalStateException.class);

    verify(
        answerRepository,
        org.mockito.Mockito.never()
    ).save(any(Answer.class));
}

@Test
@DisplayName("Likeが1件以上ある回答は編集できないこと")
void updateAnswer_HasLikes_ShouldThrowException() {

    User answerUser = new User();
    answerUser.setEmail("owner@example.com");

    Answer answer = new Answer();
    answer.setId(1L);
    answer.setUser(answerUser);

    when(
        answerRepository.findByIdAndDeletedAtIsNull(1L)
    ).thenReturn(Optional.of(answer));

    when(
        likeService.getLikeCount(answer)
    ).thenReturn(1L);

    org.assertj.core.api.Assertions
        .assertThatThrownBy(
            () -> answerService.updateAnswer(
                1L,
                "owner@example.com",
                "変更後"
            )
        )
        .isInstanceOf(IllegalStateException.class);

    verify(
        answerRepository,
        org.mockito.Mockito.never()
    ).save(any(Answer.class));
}
}