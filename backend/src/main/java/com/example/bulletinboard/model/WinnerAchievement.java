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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/*
 * 【クラス全体の役割】
 * データベースの「winner_achievements」テーブルと対応するJPAエンティティクラスです。
 *
 * このクラスでは、大喜利のお題においてユーザーが「王者」になった実績を管理します。
 *
 * RankingResultが各判定時点（7日目・14日目・21日目）の
 * ランキング履歴を保存するためのテーブルであるのに対し、
 * WinnerAchievementは「そのユーザーがそのお題で王者になった」という
 * ユーザー単位の実績を保持するために使用します。
 *
 * 【主な役割】
 * - 王者になったユーザーを保持する
 * - 王者実績の対象となったお題を保持する
 * - 実績として現在記録している王者回答を保持する
 * - 王者判定時点のいいね数を保持する
 * - 初めて王者になった日時を保持する
 * - 実績内容が更新された日時を保持する
 *
 * 【設計上のポイント】
 * - user_idとtopic_idの組み合わせにはUNIQUE制約を設定し、
 *   同じユーザーが同じお題で複数の実績レコードを持たないようにします。
 * - 7日目に王者になったユーザーが14日目や21日目でも王者になった場合、
 *   新しいレコードを追加するのではなく、既存の実績を更新する想定です。
 * - answer_idとlike_countは、その時点で実績として採用されている回答と
 *   いいね数に更新します。
 * - achievedAtは「初めて王者になった日時」を保持するため、
 *   後のランキング判定で再び王者になっても変更しません。
 * - updatedAtは実績内容が更新されるたびに更新します。
 * - TopicやAnswerが後から論理削除されても、
 *   過去に獲得した王者実績そのものは保持する設計です。
 * - User、Topic、Answerの削除に連動して実績を消さないため、
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
     * winner_achievementsテーブルの主キーで、
     * MySQLのAUTO_INCREMENTによって自動採番されます。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * 王者実績を獲得したユーザー。
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /*
     * 王者実績の対象となったお題。
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    /*
     * 実績として現在記録されている王者回答。
     *
     * 同じユーザーが後の判定時点でも王者になった場合は、
     * 必要に応じてこのAnswerを更新します。
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answer_id", nullable = false)
    private Answer answer;

    /*
     * 王者判定時点で回答が獲得していたいいね数。
     *
     * 後の判定で実績が更新された場合は、
     * 新しい判定時点のいいね数へ更新します。
     */
    @Column(name = "like_count", nullable = false)
    private int likeCount;

    /*
     * 初めて王者実績を獲得した日時。
     *
     * 同じお題で後から再び王者になった場合でも変更しません。
     */
    @Column(name = "achieved_at", nullable = false, updatable = false)
    private LocalDateTime achievedAt;

    /*
     * 王者実績の最終更新日時。
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /*
     * WinnerAchievementを初めてDBへ保存する直前に呼び出されます。
     *
     * achievedAtとupdatedAtが未設定の場合、
     * 現在日時を設定します。
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();

        if (achievedAt == null) {
            achievedAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    /*
     * WinnerAchievementを更新する直前に呼び出され、
     * updatedAtを現在日時に更新します。
     *
     * achievedAtは初回王者獲得日時として保持するため更新しません。
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}