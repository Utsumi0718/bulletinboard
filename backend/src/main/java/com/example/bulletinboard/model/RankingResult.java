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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/*
 * 【クラス全体の役割】
 * データベースの「ranking_results」テーブルと対応するJPAエンティティクラスです。
 *
 * このクラスでは、大喜利のお題ごとに行われるランキング判定の結果を
 * 履歴として保存します。
 *
 * お題の投稿日時を基準として7日目・14日目・21日目にランキング判定を行い、
 * その時点で最も多くのいいねを獲得していた回答を記録します。
 *
 * 【主な役割】
 * - どのお題のランキング結果かを保持する
 * - 王者となった回答を保持する
 * - 王者となった回答を投稿したユーザーを保持する
 * - DAY_7 / DAY_14 / DAY_21 のどの判定結果かを保持する
 * - 判定時点のいいね数をスナップショットとして保持する
 * - ランキング結果が記録された日時を保持する
 *
 * 【設計上のポイント】
 * - ランキング結果は「勝者となった回答」のみ保存します。
 * - 同率1位が複数存在する場合は、複数のRankingResultを保存できます。
 * - 回答が存在しない場合や、すべての回答のいいね数が0の場合は
 *   RankingResultを作成しない予定です。
 * - topic_id、answer_id、checkpointの組み合わせにはUNIQUE制約を設定し、
 *   同じ判定結果が重複登録されることを防ぎます。
 * - likeCountには現在のいいね数ではなく、
 *   ランキング判定を行った時点のいいね数を保存します。
 * - user_idはAnswerから取得できる情報ですが、
 *   ランキング履歴として明示的に保持するためDBにも保存します。
 * - TopicやAnswerが論理削除された場合でも、
 *   過去のランキング履歴そのものは保持する設計です。
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
    name = "ranking_results",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_ranking_results_topic_answer_checkpoint",
            columnNames = {"topic_id", "answer_id", "checkpoint"}
        )
    }
)
public class RankingResult {

    /*
     * ランキング結果ID。
     * ranking_resultsテーブルの主キーで、
     * MySQLのAUTO_INCREMENTによって自動採番されます。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * ランキング判定対象のお題。
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    /*
     * ランキング判定で勝者となった回答。
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answer_id", nullable = false)
    private Answer answer;

    /*
     * 勝者となった回答を投稿したユーザー。
     *
     * Answerからも投稿者を取得できますが、
     * ランキング履歴として明示的に保持します。
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /*
     * ランキングの判定時点。
     *
     * 想定する値：
     * DAY_7
     * DAY_14
     * DAY_21
     *
     * 現段階ではStringとして保持し、
     * 後ほどEnumとして定義する予定です。
     */
    @Column(name = "checkpoint", nullable = false, length = 20)
    private String checkpoint;

    /*
     * ランキング判定時点で回答が獲得していたいいね数。
     *
     * 後からいいね数が増減しても、
     * この値は判定時点の記録として保持します。
     */
    @Column(name = "like_count", nullable = false)
    private int likeCount;

    /*
     * ランキング結果を記録した日時。
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /*
     * RankingResultを初めてDBへ保存する直前に呼び出され、
     * createdAtが未設定の場合は現在日時を設定します。
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}