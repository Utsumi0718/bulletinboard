package com.example.bulletinboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.example.bulletinboard.dto.like.LikeResponse;
import com.example.bulletinboard.exception.AnswerNotFoundException;
import com.example.bulletinboard.exception.ForbiddenOperationException;
import com.example.bulletinboard.exception.LikeConflictException;
import com.example.bulletinboard.exception.UserNotFoundException;
import com.example.bulletinboard.model.Answer;
import com.example.bulletinboard.model.Like;
import com.example.bulletinboard.model.Topic;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.AnswerLikeCount;
import com.example.bulletinboard.repository.AnswerRepository;
import com.example.bulletinboard.repository.LikeRepository;


/**
 * 【クラスの役割】
 * LikeServiceの業務ロジックを検証する単体テストクラスです。
 *
 * LikeRepository、AnswerRepository、
 * CustomUserDetailsServiceをMockitoでモック化し、
 * 実際のDBへ接続せずにAnswerへのLike処理を検証します。
 *
 * 【主な検証内容】
 * - 未Like状態でLikeを追加できること
 * - Like済み状態でLikeを解除できること
 * - toggle後のliked状態とLike件数が正しいこと
 * - Answer不存在・論理削除済みの場合にLikeできないこと
 * - 親Topicが論理削除済みの場合にLikeできないこと
 * - 自分自身のAnswerへLikeできないこと
 * - ログインユーザーが存在しない場合に処理を拒否すること
 * - Like登録時のDB競合をLikeConflictExceptionへ変換すること
 * - Like状態とLike件数を取得できること
 *
 * 【設計上のポイント】
 * toggleLike()にはanswerIdとloginEmailを渡し、
 * User取得、Answer取得、業務ルール判定、
 * Like追加・解除をLikeService内で行います。
 */
@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private AnswerRepository answerRepository;

    @Mock
    private CustomUserDetailsService customUserDetailsService;

    @InjectMocks
    private LikeService likeService;

    private User loginUser;
    private User answerOwner;
    private Topic testTopic;
    private Answer testAnswer;

    private static final Long ANSWER_ID = 10L;
    private static final String LOGIN_EMAIL = "testuser@example.com";

    @BeforeEach
    void setUp() {

        loginUser = new User();
        loginUser.setId(1L);
        loginUser.setUsername("testuser");
        loginUser.setEmail(LOGIN_EMAIL);

        answerOwner = new User();
        answerOwner.setId(2L);
        answerOwner.setUsername("answerowner");

        testTopic = new Topic();
        testTopic.setId(100L);

        testAnswer = new Answer();
        testAnswer.setId(ANSWER_ID);
        testAnswer.setContent("テスト回答");
        testAnswer.setUser(answerOwner);
        testAnswer.setTopic(testTopic);
    }

    @Test
    @DisplayName("未Likeの回答をtoggleするとLikeが追加されliked=trueと最新件数が返ること")
    void toggleLike_WhenNotLiked_ShouldAddLike() {

        // Given
        when(customUserDetailsService.findByEmail(LOGIN_EMAIL))
                .thenReturn(Optional.of(loginUser));

        when(answerRepository.findByIdAndDeletedAtIsNull(ANSWER_ID))
                .thenReturn(Optional.of(testAnswer));

        when(likeRepository.findByUserAndAnswer(loginUser, testAnswer))
                .thenReturn(Optional.empty());

        when(likeRepository.countByAnswer(testAnswer))
                .thenReturn(1L);

        // When
        LikeResponse result =
                likeService.toggleLike(
                        ANSWER_ID,
                        LOGIN_EMAIL
                );

        // Then
        assertTrue(result.isLiked());
        assertEquals(1L, result.getLikeCount());

        verify(likeRepository, times(1))
                .saveAndFlush(any(Like.class));

        verify(likeRepository, never())
                .delete(any(Like.class));

        verify(likeRepository, times(1))
                .countByAnswer(testAnswer);
    }

    @Test
    @DisplayName("Like済みの回答をtoggleするとLikeが解除されliked=falseと最新件数が返ること")
    void toggleLike_WhenAlreadyLiked_ShouldRemoveLike() {

        // Given
        Like existingLike =
                new Like(loginUser, testAnswer);

        when(customUserDetailsService.findByEmail(LOGIN_EMAIL))
                .thenReturn(Optional.of(loginUser));

        when(answerRepository.findByIdAndDeletedAtIsNull(ANSWER_ID))
                .thenReturn(Optional.of(testAnswer));

        when(likeRepository.findByUserAndAnswer(loginUser, testAnswer))
                .thenReturn(Optional.of(existingLike));

        when(likeRepository.countByAnswer(testAnswer))
                .thenReturn(2L);

        // When
        LikeResponse result =
                likeService.toggleLike(
                        ANSWER_ID,
                        LOGIN_EMAIL
                );

        // Then
        assertFalse(result.isLiked());
        assertEquals(2L, result.getLikeCount());

        verify(likeRepository, times(1))
                .delete(existingLike);

        verify(likeRepository, never())
                .saveAndFlush(any(Like.class));

        verify(likeRepository, times(1))
                .countByAnswer(testAnswer);
    }

    @Test
    @DisplayName("存在しないAnswerへLikeするとAnswerNotFoundExceptionになること")
    void toggleLike_WhenAnswerDoesNotExist_ShouldThrowAnswerNotFoundException() {

        // Given
        when(customUserDetailsService.findByEmail(LOGIN_EMAIL))
                .thenReturn(Optional.of(loginUser));

        when(answerRepository.findByIdAndDeletedAtIsNull(ANSWER_ID))
                .thenReturn(Optional.empty());

        // When & Then
        AnswerNotFoundException exception =
                assertThrows(
                        AnswerNotFoundException.class,
                        () -> likeService.toggleLike(
                                ANSWER_ID,
                                LOGIN_EMAIL
                        )
                );

        assertEquals(
                "この回答は存在しないか、削除されています。",
                exception.getMessage()
        );

        verify(likeRepository, never())
                .findByUserAndAnswer(any(), any());

        verify(likeRepository, never())
                .saveAndFlush(any(Like.class));

        verify(likeRepository, never())
                .delete(any(Like.class));
    }

    @Test
    @DisplayName("論理削除済みAnswerへLikeするとAnswerNotFoundExceptionになること")
    void toggleLike_WhenAnswerIsDeleted_ShouldThrowAnswerNotFoundException() {

        /*
         * findByIdAndDeletedAtIsNull()は
         * 論理削除済みAnswerを取得しないため、
         * RepositoryからOptional.empty()が返る状態を再現する。
         */
        when(customUserDetailsService.findByEmail(LOGIN_EMAIL))
                .thenReturn(Optional.of(loginUser));

        when(answerRepository.findByIdAndDeletedAtIsNull(ANSWER_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                AnswerNotFoundException.class,
                () -> likeService.toggleLike(
                        ANSWER_ID,
                        LOGIN_EMAIL
                )
        );

        verify(likeRepository, never())
                .findByUserAndAnswer(any(), any());

        verify(likeRepository, never())
                .saveAndFlush(any(Like.class));

        verify(likeRepository, never())
                .delete(any(Like.class));
    }

    @Test
    @DisplayName("親Topicが論理削除済みの場合はAnswerへLikeできないこと")
    void toggleLike_WhenTopicIsDeleted_ShouldThrowAnswerNotFoundException() {

        // Given
        testTopic.setDeletedAt(LocalDateTime.now());

        when(customUserDetailsService.findByEmail(LOGIN_EMAIL))
                .thenReturn(Optional.of(loginUser));

        when(answerRepository.findByIdAndDeletedAtIsNull(ANSWER_ID))
                .thenReturn(Optional.of(testAnswer));

        // When & Then
        AnswerNotFoundException exception =
                assertThrows(
                        AnswerNotFoundException.class,
                        () -> likeService.toggleLike(
                                ANSWER_ID,
                                LOGIN_EMAIL
                        )
                );

        assertEquals(
                "この回答は存在しないか、削除されています。",
                exception.getMessage()
        );

        verify(likeRepository, never())
                .findByUserAndAnswer(any(), any());

        verify(likeRepository, never())
                .saveAndFlush(any(Like.class));

        verify(likeRepository, never())
                .delete(any(Like.class));
    }

    @Test
    @DisplayName("自分自身のAnswerへLikeするとForbiddenOperationExceptionになること")
    void toggleLike_WhenOwnAnswer_ShouldThrowForbiddenOperationException() {

        // Given
        testAnswer.setUser(loginUser);

        when(customUserDetailsService.findByEmail(LOGIN_EMAIL))
                .thenReturn(Optional.of(loginUser));

        when(answerRepository.findByIdAndDeletedAtIsNull(ANSWER_ID))
                .thenReturn(Optional.of(testAnswer));

        // When & Then
        ForbiddenOperationException exception =
                assertThrows(
                        ForbiddenOperationException.class,
                        () -> likeService.toggleLike(
                                ANSWER_ID,
                                LOGIN_EMAIL
                        )
                );

        assertEquals(
                "自分の回答にはいいねできません。",
                exception.getMessage()
        );

        verify(likeRepository, never())
                .findByUserAndAnswer(any(), any());

        verify(likeRepository, never())
                .saveAndFlush(any(Like.class));

        verify(likeRepository, never())
                .delete(any(Like.class));
    }

    @Test
    @DisplayName("ログインユーザーを取得できない場合はUserNotFoundExceptionになること")
    void toggleLike_WhenUserDoesNotExist_ShouldThrowUserNotFoundException() {

        // Given
        when(customUserDetailsService.findByEmail(LOGIN_EMAIL))
                .thenReturn(Optional.empty());

        // When & Then
        UserNotFoundException exception =
                assertThrows(
                        UserNotFoundException.class,
                        () -> likeService.toggleLike(
                                ANSWER_ID,
                                LOGIN_EMAIL
                        )
                );

        assertEquals(
                "ユーザーが見つかりません。",
                exception.getMessage()
        );

        verify(answerRepository, never())
                .findByIdAndDeletedAtIsNull(any());

        verify(likeRepository, never())
                .findByUserAndAnswer(any(), any());

        verify(likeRepository, never())
                .saveAndFlush(any(Like.class));
    }

    @Test
    @DisplayName("Like登録時にDB競合が発生するとLikeConflictExceptionへ変換されること")
    void toggleLike_WhenLikeInsertConflicts_ShouldThrowLikeConflictException() {

        // Given
        when(customUserDetailsService.findByEmail(LOGIN_EMAIL))
                .thenReturn(Optional.of(loginUser));

        when(answerRepository.findByIdAndDeletedAtIsNull(ANSWER_ID))
                .thenReturn(Optional.of(testAnswer));

        when(likeRepository.findByUserAndAnswer(loginUser, testAnswer))
                .thenReturn(Optional.empty());

        when(likeRepository.saveAndFlush(any(Like.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "unique constraint violation"
                ));

        // When & Then
        LikeConflictException exception =
                assertThrows(
                        LikeConflictException.class,
                        () -> likeService.toggleLike(
                                ANSWER_ID,
                                LOGIN_EMAIL
                        )
                );

        assertEquals(
                "この回答にはすでにいいねしています。",
                exception.getMessage()
        );

        verify(likeRepository, times(1))
                .saveAndFlush(any(Like.class));

        verify(likeRepository, never())
                .countByAnswer(any());
    }

    @Test
    @DisplayName("ユーザーがAnswerをLike済みの場合はtrueを返すこと")
    void isLikedByUser_WhenLiked_ShouldReturnTrue() {

        // Given
        when(likeRepository.existsByUserAndAnswer(
                loginUser,
                testAnswer
        )).thenReturn(true);

        // When
        boolean result =
                likeService.isLikedByUser(
                        loginUser,
                        testAnswer
                );

        // Then
        assertTrue(result);

        verify(likeRepository, times(1))
                .existsByUserAndAnswer(
                        loginUser,
                        testAnswer
                );
    }

    @Test
    @DisplayName("ユーザーがAnswerをLikeしていない場合はfalseを返すこと")
    void isLikedByUser_WhenNotLiked_ShouldReturnFalse() {

        // Given
        when(likeRepository.existsByUserAndAnswer(
                loginUser,
                testAnswer
        )).thenReturn(false);

        // When
        boolean result =
                likeService.isLikedByUser(
                        loginUser,
                        testAnswer
                );

        // Then
        assertFalse(result);

        verify(likeRepository, times(1))
                .existsByUserAndAnswer(
                        loginUser,
                        testAnswer
                );
    }

    @Test
    @DisplayName("指定したAnswerのLike件数を取得できること")
    void getLikeCount_ShouldReturnCount() {

        // Given
        when(likeRepository.countByAnswer(testAnswer))
                .thenReturn(3L);

        // When
        long result =
                likeService.getLikeCount(testAnswer);

        // Then
        assertEquals(3L, result);

        verify(likeRepository, times(1))
                .countByAnswer(testAnswer);
    }


    @Test
    @DisplayName("複数AnswerのLike件数を一括取得しLike0件のAnswerは0で補完されること")
    void getLikeCountsByAnswerIds_ShouldReturnLikeCountsIncludingZero() {

    // Given
    List<Long> answerIds =
            List.of(10L, 20L, 30L);

    AnswerLikeCount answer10Count =
            mock(AnswerLikeCount.class);

    AnswerLikeCount answer20Count =
            mock(AnswerLikeCount.class);

    when(answer10Count.getAnswerId())
            .thenReturn(10L);

    when(answer10Count.getLikeCount())
            .thenReturn(2L);

    when(answer20Count.getAnswerId())
            .thenReturn(20L);

    when(answer20Count.getLikeCount())
            .thenReturn(1L);

    when(
            likeRepository.countLikesByAnswerIds(answerIds)
    ).thenReturn(
            List.of(
                    answer10Count,
                    answer20Count
            )
    );

    // When
    Map<Long, Long> result =
            likeService.getLikeCountsByAnswerIds(
                    answerIds
            );

    // Then
    assertThat(result)
            .containsEntry(10L, 2L)
            .containsEntry(20L, 1L)
            .containsEntry(30L, 0L);

    assertThat(result).hasSize(3);

    verify(
            likeRepository
    ).countLikesByAnswerIds(answerIds);
}

@Test
@DisplayName("Answer ID一覧が空の場合は空Mapを返しLikeRepositoryを呼び出さないこと")
void getLikeCountsByAnswerIds_WhenAnswerIdsEmpty_ShouldReturnEmptyMap() {

    // Given
    List<Long> answerIds =
            List.of();

    // When
    Map<Long, Long> result =
            likeService.getLikeCountsByAnswerIds(
                    answerIds
            );

    // Then
    assertThat(result).isEmpty();

    verify(
            likeRepository,
            never()
    ).countLikesByAnswerIds(
            anyList()
    );
}
}