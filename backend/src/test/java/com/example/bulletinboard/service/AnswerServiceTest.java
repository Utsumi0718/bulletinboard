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

import com.example.bulletinboard.exception.AnswerNotFoundException;
import com.example.bulletinboard.exception.ForbiddenOperationException;
import com.example.bulletinboard.model.Answer;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.AnswerRepository;

/*
 * 【クラスの役割】
 * AnswerServiceのビジネスロジックを検証する
 * 単体テストクラスです。
 *
 * AnswerRepositoryとEntityManagerをMockitoでモック化し、
 * 実際のデータベースには接続せず、
 * AnswerService単体の処理を検証します。
 *
 * 【主な検証内容】
 * - Answerの保存
 * - Topicに紐づくAnswer一覧取得
 * - AnswerのIDによる取得
 *
 * - Answer削除
 *   → 投稿者本人による論理削除
 *   → ROLE_ADMINによる他ユーザーAnswerの論理削除
 *   → 権限のないユーザーはForbiddenOperationException
 *   → Answer不存在時はAnswerNotFoundException
 *
 * 【設計上のポイント】
 * - 通常の取得対象はdeletedAtがNULLのAnswerのみです。
 *
 * - Answer削除は投稿者本人またはROLE_ADMINのみ可能です。
 *
 * - Answer削除時の権限エラーには
 *   ForbiddenOperationExceptionを使用します。
 *
 * - Answer不存在・論理削除済みの場合は、
 *   削除時にAnswerNotFoundExceptionを使用します。
 */

@ExtendWith(MockitoExtension.class)
class AnswerServiceTest {

    @Mock private jakarta.persistence.EntityManager entityManager;

    @org.junit.jupiter.api.BeforeEach
    void parentLock() {
        org.springframework.test.util.ReflectionTestUtils.setField(answerService,"entityManager",entityManager);
        org.mockito.Mockito.lenient().when(answerRepository.findReportableTopicIdByAnswerId(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(Optional.of(1L));
        org.mockito.Mockito.lenient().when(entityManager.find(org.mockito.ArgumentMatchers.eq(com.example.bulletinboard.model.Topic.class),
                org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)))
                .thenAnswer(inv -> {var t=new com.example.bulletinboard.model.Topic();t.setId(1L);return t;});
    }

    @Mock
    private AnswerRepository answerRepository;

    @InjectMocks
    private AnswerService answerService;

    @Test
    @DisplayName("回答を保存できること")
    void saveAnswer_ShouldSaveAnswer() {

        Answer answer = new Answer();
        answer.setContent("テスト回答");
        var parent = new com.example.bulletinboard.model.Topic();parent.setId(1L);answer.setTopic(parent);

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
    answerUser.setEmail("3125414876");
answerUser.setId(3125414876L);

    Answer answer = new Answer();
    answer.setId(1L);
    answer.setUser(answerUser);

    when(
        answerRepository
            .findReportableByIdForUpdate(1L)
    ).thenReturn(Optional.of(answer));

    when(
        answerRepository.save(any(Answer.class))
    ).thenAnswer(
        invocation -> invocation.getArgument(0)
    );

    answerService.deleteAnswer(
        1L,
        "3125414876",
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
@DisplayName("管理者は他ユーザーのAnswerを削除できること")
void deleteAnswer_Admin_ShouldSetDeletedAt() {

    User answerUser = new User();
    answerUser.setEmail("1008766193");
answerUser.setId(1008766193L);

    Answer answer = new Answer();
    answer.setId(1L);
    answer.setUser(answerUser);

    when(
        answerRepository.findReportableByIdForUpdate(1L)
    ).thenReturn(Optional.of(answer));

    when(
        answerRepository.save(any(Answer.class))
    ).thenAnswer(
        invocation -> invocation.getArgument(0)
    );

    answerService.deleteAnswer(
        1L,
        "2291307405",
        true
    );

    assertThat(answer.getDeletedAt())
        .isNotNull();

    verify(
        answerRepository,
        times(1)
    ).save(answer);
}

@Test
@DisplayName("投稿者本人でも管理者でもない場合はForbiddenOperationExceptionになること")
void deleteAnswer_NotOwnerAndNotAdmin_ShouldThrowException() {

    User answerUser = new User();
    answerUser.setEmail("1008766193");
answerUser.setId(1008766193L);

    Answer answer = new Answer();
    answer.setId(1L);
    answer.setUser(answerUser);

    when(
            answerRepository.findReportableByIdForUpdate(1L)
    ).thenReturn(Optional.of(answer));

    org.assertj.core.api.Assertions
            .assertThatThrownBy(
                    () -> answerService.deleteAnswer(
                            1L,
                            "2084992462",
                            false
                    )
            )
            .isInstanceOf(ForbiddenOperationException.class)
            .hasMessage("この回答を削除する権限がありません。");

    verify(
            answerRepository,
            org.mockito.Mockito.never()
    ).save(any(Answer.class));
}

@Test
@DisplayName("存在しないAnswerを削除しようとするとAnswerNotFoundExceptionになること")
void deleteAnswer_AnswerNotFound_ShouldThrowException() {

    when(
            answerRepository.findReportableByIdForUpdate(999L)
    ).thenReturn(Optional.empty());

    org.assertj.core.api.Assertions
            .assertThatThrownBy(
                    () -> answerService.deleteAnswer(
                            999L,
                            "2889091152",
                            false
                    )
            )
            .isInstanceOf(AnswerNotFoundException.class)
            .hasMessage("この回答は存在しないか、削除されています。");

    verify(
            answerRepository,
            org.mockito.Mockito.never()
    ).save(any(Answer.class));
}

}
