package com.example.bulletinboard.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/*
 * 【クラス全体の役割】
 * データベースの「winner_achievements」テーブルと対応するJPAエンティティクラスです。
 *
 * このクラスでは、ユーザーがあるTopicで初めて王者になった実績を保持します。
 *
 * RankingResultがDAY_7 / DAY_14 / DAY_21それぞれの
 * 王者判定結果を履歴として保存するのに対し、
 *
 * WinnerAchievementは
 * 「このユーザーが、このTopicで王者実績を獲得した」
 * というUser × Topic単位の実績を保存します。
 *
 * 【1レコードの意味】
 * 1人のUserが、1つのTopicで獲得した王者実績1件を表します。
 *
 * 同じUserが同じTopicで複数回王者になった場合でも、
 * WinnerAchievementは1件のみ保持します。
 *
 * 別のTopicで王者になった場合は、
 * 新しいWinnerAchievementを作成します。
 *
 * 【主な役割】
 * - 王者実績を獲得したUserを保持する
 * - 王者実績の対象となったTopicを保持する
 * - 初めて王者実績を獲得したときの代表Answerを保持する
 * - 初回王者判定時点のLike数を保持する
 * - 初めて王者実績を獲得した日時を保持する
 *
 * 【設計上のポイント】
 * - user_idとtopic_idの組み合わせにはUNIQUE制約を設定し、
 *   同じUser × Topicの王者実績が重複登録されることを防ぎます。
 *
 * - DAY_7で王者になったUserが、
 *   DAY_14やDAY_21でも同じTopicで再び王者になった場合でも、
 *   新しいWinnerAchievementは作成しません。
 *
 * - 一度作成したWinnerAchievementの
 *   answer、likeCount、achievedAtは基本的に更新しません。
 *
 * - answerには、
 *   そのUserがそのTopicで初めて王者実績を獲得したときの
 *   代表王者回答を保持します。
 *
 * - 初回王者判定で同一Userの複数Answerが同率王者になった場合は、
 *   最も先に投稿されたAnswerを代表として保存します。
 *
 * - likeCountには、
 *   初めて王者実績を獲得した判定時点のLike数を保存します。
 *
 * - achievedAtには、
 *   初めて王者実績を獲得した日時を保存します。
 *
 * - TopicやAnswerが後から論理削除されても、
 *   過去に獲得した王者実績そのものは保持します。
 *
 * - User、Topic、Answerの削除に連動して実績を削除しないため、
 *   CascadeType.ALLやorphanRemoval=trueは使用しません。
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
    name = "winner_achievements",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_winner_achievements_user_topic",
            columnNames = {"user_id", "topic_id"}
        )
    }
)
public class WinnerAchievement {

    /*
     * 王者実績ID。
     *
     * winner_achievementsテーブルの主キーで、
     * MySQLのAUTO_INCREMENTによって自動採番されます。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * 王者実績を獲得したユーザー。
     *
     * WinnerAchievementはUser × Topic単位の実績なので、
     * 実績の所有者となるUserを直接保持します。
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /*
     * 王者実績を獲得した対象のお題。
     *
     * 同じUserでも別のTopicで王者になった場合は、
     * それぞれ別のWinnerAchievementを作成します。
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    /*
     * 初めて王者実績を獲得したときの代表王者回答。
     *
     * 後のcheckpointで同じUserの別Answerが王者になっても、
     * このAnswerは更新しません。
     *
     * 初回王者判定で同一Userの複数Answerが同率王者の場合は、
     * 最も先に投稿されたAnswerを代表として保存します。
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answer_id", nullable = false)
    private Answer answer;

    /*
     * 初めて王者実績を獲得した判定時点のいいね数。
     *
     * 後からLike数が増減した場合や、
     * 後のcheckpointで再び王者になった場合でも更新しません。
     */
    @Column(name = "like_count", nullable = false)
    private int likeCount;

    /*
     * 初めて王者実績を獲得した日時。
     *
     * 単なるレコード作成日時ではなく、
     * 初回王者獲得時の日時として保持します。
     *
     * 値は王者判定を行うService側から明示的に設定し、
     * 保存後は更新しません。
     */
    @Column(name = "achieved_at", nullable = false, updatable = false)
    private LocalDateTime achievedAt;
}