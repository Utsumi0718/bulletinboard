package com.example.bulletinboard.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.bulletinboard.model.Topic;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.model.WinnerAchievement;

/*
 * 【クラス全体の役割】
 * WinnerAchievementのデータベース操作を担当するRepositoryです。
 *
 * WinnerAchievementは、
 * 「どのUserが、どのTopicで王者実績を獲得したか」
 * をUser × Topic単位で管理するEntityです。
 *
 * このRepositoryでは、
 * WinnerAchievementの登録・取得などのDB操作を担当します。
 *
 * 「すでに王者実績を持っているので新規作成しない」
 * 「まだ王者実績がないので新規作成する」
 * といった業務上の判断はRepositoryでは行わず、
 * Service側で行います。
 */
public interface WinnerAchievementRepository
        extends JpaRepository<WinnerAchievement, Long> {

    /*
     * 指定したUserとTopicに対応する
     * WinnerAchievementを取得します。
     *
     * DBではuser_idとtopic_idの組み合わせに
     * UNIQUE制約が設定されているため、
     * 検索結果は0件または1件になります。
     *
     * まだ王者実績を獲得していない場合もあるため、
     * 戻り値にはOptionalを使用します。
     *
     * 取得結果を使って、
     * 「既存実績があるため新規作成しない」
     * と判断するのはService側の役割です。
     */
    Optional<WinnerAchievement> findByUserAndTopic(
            User user,
            Topic topic
    );

    /*
     * 指定したUserが獲得した王者実績を、
     * achievedAtの新しい順にすべて取得します。
     *
     * 1人のUserが複数のTopicで
     * 王者実績を獲得する可能性があるため、
     * 戻り値にはListを使用します。
     *
     * OrderByAchievedAtDescによって、
     * achievedAtを降順に並べ、
     * 新しく獲得した実績から取得します。
     *
     * 主に将来のプロフィール画面で、
     * ユーザーの王者実績一覧を表示するために使用します。
     */
    List<WinnerAchievement> findByUserOrderByAchievedAtDesc(
            User user
    );
}