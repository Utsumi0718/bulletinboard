package com.example.bulletinboard.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

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
 *
 * 【設計上のポイント】
 * - 旧LikeRepositoryではPostへのいいねを管理していましたが、
 *   新しい設計ではAnswerへのいいねを管理します。
 * - 同じユーザーが同じ回答に複数回いいねできないことは、
 *   likesテーブルのUNIQUE(user_id, answer_id)制約でも保証しています。
 * - 自分自身の回答へのいいね禁止はDB制約ではなく、
 *   Service層で判定します。
 * - 論理削除済みのAnswerへのいいね禁止も、
 *   Service層で判定する予定です。
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
}