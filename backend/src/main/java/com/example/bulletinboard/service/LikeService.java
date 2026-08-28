package com.example.bulletinboard.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.bulletinboard.model.Answer;
import com.example.bulletinboard.model.Like;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.LikeRepository;

/*
 * 【クラスの役割】
 * 大喜利サービスの「いいね（Like）」に関する
 * ビジネスロジックを担当するServiceクラスです。
 *
 * LikeRepositoryを通して、
 * 回答へのいいねの登録・解除・存在確認・件数取得を行います。
 *
 * 【主な役割】
 * - 回答へのいいね登録
 * - 回答へのいいね解除
 * - ログインユーザーが回答をいいね済みか確認
 * - 回答に付いているいいね総数の取得
 *
 * 【設計上のポイント】
 * - 旧LikeServiceではPostに対していいねを付けていましたが、
 *   新しい設計ではAnswerに対していいねを付けます。
 * - 同じユーザーが同じ回答に複数回いいねできないことは、
 *   Serviceでの存在確認に加えて、
 *   likesテーブルのUNIQUE(user_id, answer_id)制約でも保証します。
 * - 「自分自身の回答にはいいねできない」
 *   「削除済み回答にはいいねできない」などの詳細な業務ルールは、
 *   feature/like-featureで実装します。
 */
@Service
@Transactional(readOnly = true)
public class LikeService {

    private final LikeRepository likeRepository;

    /*
     * LikeRepositoryをコンストラクタインジェクションします。
     */
    public LikeService(LikeRepository likeRepository) {
        this.likeRepository = likeRepository;
    }

    /*
     * 回答へのいいねを切り替えます。
     *
     * すでにいいねしている場合：
     *   いいねを削除してfalseを返します。
     *
     * まだいいねしていない場合：
     *   新しくいいねを保存してtrueを返します。
     */
    @Transactional
    public boolean toggleLike(User user, Answer answer) {

        if (likeRepository.existsByUserAndAnswer(user, answer)) {

            likeRepository.deleteByUserAndAnswer(user, answer);

            return false;
        }

        likeRepository.save(
            new Like(user, answer)
        );

        return true;
    }

    /*
     * 指定したユーザーが、
     * 指定した回答をすでにいいねしているか確認します。
     *
     * userまたはanswerがnullの場合はfalseを返します。
     */
    public boolean isLikedByUser(User user, Answer answer) {

        if (user == null || answer == null) {
            return false;
        }

        return likeRepository.existsByUserAndAnswer(
            user,
            answer
        );
    }

    /*
     * 指定した回答に付いている
     * いいねの総数を取得します。
     */
    public long getLikeCount(Answer answer) {

        if (answer == null) {
            return 0;
        }

        return likeRepository.countByAnswer(answer);
    }
}