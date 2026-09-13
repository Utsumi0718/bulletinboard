package com.example.bulletinboard.model;

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
 * データベースの「ranking_results」テーブルと対応するJPAエンティティクラスです。
 *
 * このクラスでは、王者判定の結果として
 * 王者になった回答を履歴として保存します。
 *
 * RankingJudgmentが
 * 「どのお題について、どのチェックポイントで、いつ判定したか」
 * を保持するのに対し、
 *
 * RankingResultは
 * 「その判定で、どの回答が何いいねで王者になったか」
 * を保持します。
 *
 * 【1レコードの意味】
 * 1回の王者判定で王者になった1つの回答を表します。
 *
 * 同率1位の回答が複数存在する場合は、
 * 同じRankingJudgmentに対して複数のRankingResultを保存します。
 *
 * 【主な役割】
 * - どのRankingJudgmentによる王者結果かを保持する
 * - 王者となった回答を保持する
 * - 判定時点のいいね数をスナップショットとして保持する
 *
 * 【設計上のポイント】
 * - ランキング結果は王者となった回答のみ保存します。
 *
 * - 回答が存在しない場合や、
 *   すべての回答のいいね数が0の場合は
 *   RankingResultを作成しません。
 *
 * - 王者なしの場合でもRankingJudgmentは保存されるため、
 *   「未判定」と「判定済み・王者なし」を区別できます。
 *
 * - ranking_judgment_id と answer_id の組み合わせには
 *   UNIQUE制約を設定し、
 *   同じ判定で同じ回答が重複登録されることを防ぎます。
 *
 * - likeCountには現在のいいね数ではなく、
 *   王者判定を行った時点のいいね数を保存します。
 *
 * - Topic、checkpoint、判定日時は
 *   RankingJudgmentから取得できるため、
 *   RankingResultでは直接保持しません。
 *
 * - UserはAnswerから取得できるため、
 *   RankingResultではuser_idを直接保持しません。
 *
 * - TopicやAnswerが論理削除された場合でも、
 *   過去のランキング結果そのものは保持する設計です。
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
    name = "ranking_results",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_ranking_results_ranking_judgment_answer",
            columnNames = {"ranking_judgment_id", "answer_id"}
        )
    }
)
public class RankingResult {

    /*
     * ランキング結果ID。
     *
     * ranking_resultsテーブルの主キーで、
     * MySQLのAUTO_INCREMENTによって自動採番されます。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * この王者結果を生み出したランキング判定。
     *
     * 複数のRankingResultが
     * 1つのRankingJudgmentに紐づく可能性があるため、
     * RankingResult側はManyToOneの関係になります。
     *
     * DBではranking_judgment_idを外部キーとして
     * ranking_judgmentsテーブルを参照します。
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ranking_judgment_id", nullable = false)
    private RankingJudgment rankingJudgment;

    /*
     * ランキング判定で王者となった回答。
     *
     * 同じAnswerがDAY_7、DAY_14、DAY_21など
     * 複数の判定で王者になる可能性があるため、
     * RankingResult側はManyToOneの関係になります。
     *
     * DBではanswer_idを外部キーとして
     * answersテーブルを参照します。
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answer_id", nullable = false)
    private Answer answer;

    /*
     * 王者判定時点で回答が獲得していたいいね数。
     *
     * 現在のいいね数ではなく、
     * 判定時点の値をスナップショットとして保存します。
     *
     * 判定後にいいね数が増減しても、
     * 過去のRankingResultのlikeCountは変更しません。
     */
    @Column(name = "like_count", nullable = false)
    private int likeCount;
}