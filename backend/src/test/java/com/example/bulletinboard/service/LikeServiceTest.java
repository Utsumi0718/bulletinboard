package com.example.bulletinboard.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.bulletinboard.model.Answer;
import com.example.bulletinboard.model.Like;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.LikeRepository;

/*
 * 【クラスの役割】
 * LikeServiceのビジネスロジックを検証する単体テストクラスです。
 *
 * LikeRepositoryをMockitoでモック化し、
 * 実際のデータベースには接続せずに、
 * 回答（Answer）へのいいね登録・解除・存在確認・件数取得を検証します。
 *
 * 【主な検証内容】
 * - 未いいね状態でtoggleLikeを実行するとLikeが保存されること
 * - いいね済み状態でtoggleLikeを実行するとLikeが削除されること
 * - ユーザーが回答をいいね済みの場合にtrueを返すこと
 * - UserまたはAnswerがnullの場合にfalseを返すこと
 * - 指定したAnswerのLike件数を取得できること
 * - Answerがnullの場合にLike件数0を返すこと
 *
 * 【設計上のポイント】
 * - 旧LikeServiceではPostへのいいねを扱っていましたが、
 *   新しい仕様ではAnswerへのいいねを扱います。
 * - Likeの存在確認にはLikeRepository.existsByUserAndAnswer()を使用します。
 * - Like件数の取得にはLikeRepository.countByAnswer()を使用します。
 * - nullが渡された場合はRepositoryへアクセスせず、
 *   isLikedByUser()はfalse、getLikeCount()は0を返します。
 * - 自分自身の回答へのいいね禁止や、
 *   論理削除済みAnswerへのいいね禁止などの詳細ルールは、
 *   feature/like-featureで追加実装・テストを行います。
 */


@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    @Mock
    private LikeRepository likeRepository;

    @InjectMocks
    private LikeService likeService;

    private User testUser;
    private Answer testAnswer;

    @BeforeEach
    void setUp() {

        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");

        testAnswer = new Answer();
        testAnswer.setId(10L);
        testAnswer.setContent("テスト回答");
    }

    @Test
    @DisplayName("未いいねの回答にtoggleLikeを実行するとLikeが新規保存されること")
    void toggleLike_WhenNotLiked_ShouldSaveLike() {

        /*
         * Given:
         * このユーザーはまだ対象回答へ
         * いいねしていない状態。
         */
        when(
            likeRepository.existsByUserAndAnswer(
                testUser,
                testAnswer
            )
        ).thenReturn(false);

        /*
         * When:
         * toggleLikeを実行。
         */
        boolean result =
            likeService.toggleLike(
                testUser,
                testAnswer
            );

        /*
         * Then:
         * trueが返り、Likeが保存されること。
         */
        assertTrue(result);

        verify(
            likeRepository,
            times(1)
        ).save(any(Like.class));

        verify(
            likeRepository,
            never()
        ).deleteByUserAndAnswer(
            any(),
            any()
        );
    }

    @Test
    @DisplayName("いいね済みの回答にtoggleLikeを実行するとLikeが解除されること")
    void toggleLike_WhenAlreadyLiked_ShouldDeleteLike() {

        /*
         * Given:
         * このユーザーはすでに対象回答へ
         * いいねしている状態。
         */
        when(
            likeRepository.existsByUserAndAnswer(
                testUser,
                testAnswer
            )
        ).thenReturn(true);

        /*
         * When:
         * toggleLikeを実行。
         */
        boolean result =
            likeService.toggleLike(
                testUser,
                testAnswer
            );

        /*
         * Then:
         * falseが返り、Likeが削除されること。
         */
        assertFalse(result);

        verify(
            likeRepository,
            times(1)
        ).deleteByUserAndAnswer(
            testUser,
            testAnswer
        );

        verify(
            likeRepository,
            never()
        ).save(any());
    }

    @Test
@DisplayName("ユーザーが回答をいいね済みの場合はtrueを返すこと")
void isLikedByUser_WhenLiked_ShouldReturnTrue() {

    when(
        likeRepository.existsByUserAndAnswer(
            testUser,
            testAnswer
        )
    ).thenReturn(true);

    boolean result =
        likeService.isLikedByUser(
            testUser,
            testAnswer
        );

    assertTrue(result);

    verify(
        likeRepository,
        times(1)
    ).existsByUserAndAnswer(
        testUser,
        testAnswer
    );
}

@Test
@DisplayName("UserまたはAnswerがnullの場合はfalseを返すこと")
void isLikedByUser_WhenArgumentIsNull_ShouldReturnFalse() {

    assertFalse(
        likeService.isLikedByUser(
            null,
            testAnswer
        )
    );

    assertFalse(
        likeService.isLikedByUser(
            testUser,
            null
        )
    );

    verify(
        likeRepository,
        never()
    ).existsByUserAndAnswer(
        any(),
        any()
    );
}

@Test
@DisplayName("指定したAnswerのLike件数を取得できること")
void getLikeCount_ShouldReturnCount() {

    when(
        likeRepository.countByAnswer(testAnswer)
    ).thenReturn(3L);

    long result =
        likeService.getLikeCount(testAnswer);

    assertEquals(
        3L,
        result
    );

    verify(
        likeRepository,
        times(1)
    ).countByAnswer(testAnswer);
}

@Test
@DisplayName("Answerがnullの場合はLike件数0を返すこと")
void getLikeCount_WhenAnswerIsNull_ShouldReturnZero() {

    long result =
        likeService.getLikeCount(null);

    assertEquals(
        0L,
        result
    );

    verify(
        likeRepository,
        never()
    ).countByAnswer(any());
}
}