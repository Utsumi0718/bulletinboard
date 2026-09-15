package com.example.bulletinboard.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

/*
 * 【クラスの役割】
 * LikeRepositoryが正しくデータベース操作を行えるか検証する
 * Repository層のテストクラスです。
 *
 * 【主な検証内容】
 * - 複数AnswerのLike件数を一括取得できること
 * - Likeが0件のAnswerは集計結果に含まれないこと
 * - 指定ユーザーがLike済みのAnswer IDを一括取得できること
 * - 他ユーザーのLikeがliked判定へ混ざらないこと
 *
 * Answer一覧表示時に、
 * Answerごとにcount / existsクエリを実行せず、
 * 一括取得Queryが正しく動作することを確認します。
 */
@DataJpaTest
@ActiveProfiles("default")
@AutoConfigureTestDatabase(
    replace = AutoConfigureTestDatabase.Replace.NONE
)
class LikeRepositoryTest {

    @Autowired
    private LikeRepository likeRepository;

    @Test
    @DisplayName("複数AnswerのLike件数を一括取得できること")
    @Sql("LikeRepositoryTest.sql")
    void countLikesByAnswerIds_ShouldReturnLikeCounts() {

        // When
        List<AnswerLikeCount> results =
                likeRepository.countLikesByAnswerIds(
                        List.of(1L, 2L, 3L)
                );

        // Then
        assertThat(results).hasSize(2);

        AnswerLikeCount answer1 =
                results.stream()
                        .filter(
                                result ->
                                        result.getAnswerId().equals(1L)
                        )
                        .findFirst()
                        .orElseThrow();

        assertThat(answer1.getLikeCount())
                .isEqualTo(2L);

        AnswerLikeCount answer2 =
                results.stream()
                        .filter(
                                result ->
                                        result.getAnswerId().equals(2L)
                        )
                        .findFirst()
                        .orElseThrow();

        assertThat(answer2.getLikeCount())
                .isEqualTo(1L);

        /*
         * Answer ID = 3 にはLikeが存在しないため、
         * GROUP BYの集計結果には含まれないことを確認します。
         *
         * Like 0件の補完はService層で行います。
         */
        assertThat(results)
                .noneMatch(
                        result ->
                                result.getAnswerId().equals(3L)
                );
    }

    @Test
    @DisplayName("指定ユーザーがLike済みのAnswer IDだけを一括取得できること")
    @Sql("LikeRepositoryTest.sql")
    void findLikedAnswerIdsByUserIdAndAnswerIds_ShouldReturnLikedAnswerIds() {

        // When
        List<Long> likedAnswerIds =
                likeRepository
                        .findLikedAnswerIdsByUserIdAndAnswerIds(
                                2L,
                                List.of(1L, 2L, 3L)
                        );

        // Then
        assertThat(likedAnswerIds)
                .containsExactlyInAnyOrder(1L);

        /*
         * Answer ID = 2 には別ユーザー（User ID = 3）の
         * Likeが存在しますが、
         * User ID = 2 のliked判定には含まれないことを確認します。
         */
        assertThat(likedAnswerIds)
                .doesNotContain(2L, 3L);
    }
}