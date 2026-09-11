package com.example.bulletinboard.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.bulletinboard.model.Answer;
import com.example.bulletinboard.model.Like;
import com.example.bulletinboard.model.User;

/*
 * 【クラス（インターフェース）の役割】
 * データベースの「likes」テーブルに対するデータ操作を担当する
 * Spring Data JPAのRepositoryインターフェースです。
 *
 * Likeエンティティを対象として、
 * 回答へのいいねの存在確認、取得、件数集計、削除などを行います。
 *
 * 【主な役割】
 * - Likeの保存
 * - ユーザーが特定の回答にいいね済みか確認する
 * - ユーザーと回答の組み合わせからLikeを取得する
 * - 特定の回答に付いているいいね数を取得する
 * - ユーザーが特定の回答に付けたいいねを削除する
 * - 複数AnswerのLike件数を一括取得する
 * - ログインユーザーがLike済みのAnswer IDを一括取得する
 *
 * 【設計上のポイント】
 * - 同じユーザーが同じ回答に複数回いいねできないことは、
 *   likesテーブルのUNIQUE(user_id, answer_id)制約でも保証しています。
 *
 * - 自分自身の回答へのいいね禁止は、
 *   Service層で判定します。
 *
 * - Answer一覧取得時は、
 *   AnswerごとにLike件数・liked状態を問い合わせず、
 *   一括Queryを使用してN+1を避けます。
 */
public interface LikeRepository extends JpaRepository<Like, Long> {

    /*
     * 指定したユーザーが指定した回答に
     * すでにいいねしているか確認します。
     */
    boolean existsByUserAndAnswer(User user, Answer answer);

    /*
     * 指定したユーザーと回答の組み合わせから
     * Likeを取得します。
     *
     * 主に、いいね解除処理で使用します。
     */
    Optional<Like> findByUserAndAnswer(User user, Answer answer);

    /*
     * 指定した回答に付いている
     * いいねの総数を取得します。
     */
    long countByAnswer(Answer answer);

    /*
     * 指定したユーザーが指定した回答に付けた
     * いいねを削除します。
     */
    void deleteByUserAndAnswer(User user, Answer answer);

    /*
     * 指定された複数のAnswerについて、
     * AnswerごとのLike件数を一括取得します。
     *
     * Likeが0件のAnswerは結果に含まれないため、
     * Service側で0件として補完します。
     */
    @Query("""
            SELECT
                l.answer.id AS answerId,
                COUNT(l.id) AS likeCount
            FROM Like l
            WHERE l.answer.id IN :answerIds
            GROUP BY l.answer.id
            """)
    List<AnswerLikeCount> countLikesByAnswerIds(
            @Param("answerIds") List<Long> answerIds
    );

    /*
     * 指定されたユーザーがLikeしているAnswerのうち、
     * 指定されたAnswer ID一覧に含まれるAnswer IDを
     * 一括取得します。
     */
    @Query("""
            SELECT l.answer.id
            FROM Like l
            WHERE l.user.id = :userId
              AND l.answer.id IN :answerIds
            """)
    List<Long> findLikedAnswerIdsByUserIdAndAnswerIds(
            @Param("userId") Long userId,
            @Param("answerIds") List<Long> answerIds
    );
}