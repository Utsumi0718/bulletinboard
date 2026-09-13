package com.example.bulletinboard.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * データベースの「ranking_judgments」テーブルと対応するJPAエンティティクラスです。
 *
 * このクラスでは、Topicについて
 * DAY_7 / DAY_14 / DAY_21 の王者判定を実施した事実を記録します。
 *
 * RankingJudgmentは「誰が王者になったか」を保存するテーブルではなく、
 * 「どのお題について、どのチェックポイントで、いつ判定したか」
 * を保存するためのEntityです。
 *
 * 王者となった回答は、RankingResultに保存します。
 *
 * 【1レコードの意味】
 * 1つのTopicに対する、
 * 1つのcheckpointの王者判定1回を表します。
 *
 * 例：
 * Topic 10についてDAY_7の王者判定を実施した記録。
 *
 * 【主な役割】
 * - どのTopicについて王者判定を行ったかを保持する
 * - DAY_7 / DAY_14 / DAY_21 のどの判定かを保持する
 * - 実際に王者判定を行った日時を保持する
 *
 * 【設計上のポイント】
 * - 同じTopicについて、
 *   同じcheckpointの判定記録を複数作成しないように、
 *   topic_idとcheckpointの組み合わせにUNIQUE制約を設定します。
 *
 * - 王者が存在しない場合でもRankingJudgmentは保存します。
 *
 * - RankingJudgmentが存在し、
 *   それに紐づくRankingResultが0件の場合は、
 *   「判定済み・王者なし」と判断できます。
 *
 * - judgedAtは単なるレコード作成日時ではなく、
 *   実際に王者判定を行った日時です。
 *   そのためEntityの@PrePersistでは自動設定せず、
 *   王者判定を行うService側から明示的に設定します。
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
    name = "ranking_judgments",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_ranking_judgments_topic_checkpoint",
            columnNames = {"topic_id", "checkpoint"}
        )
    }
)
public class RankingJudgment {

    /*
     * 王者判定記録ID。
     *
     * ranking_judgmentsテーブルの主キーで、
     * MySQLのAUTO_INCREMENTによって自動採番されます。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * 王者判定の対象となったお題。
     *
     * 1つのTopicに対して、
     * DAY_7 / DAY_14 / DAY_21 など複数のRankingJudgmentが
     * 作成される可能性があるため、
     * RankingJudgment側はManyToOneの関係になります。
     *
     * DBではtopic_idを外部キーとして
     * topicsテーブルを参照します。
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    /*
     * 王者判定を行ったチェックポイント。
     *
     * RankingCheckpoint Enumを使用し、
     * 以下のいずれかを保持します。
     *
     * DAY_7
     * DAY_14
     * DAY_21
     *
     * EnumType.STRINGを指定することで、
     * DBにも「DAY_7」などの文字列として保存します。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "checkpoint", nullable = false, length = 20)
    private RankingCheckpoint checkpoint;

    /*
     * 実際に王者判定を行った日時。
     *
     * Topic投稿から7日後・14日後・21日後という
     * checkpointそのものの日時ではなく、
     * 実際に判定処理を実行した日時を保持します。
     *
     * 値は王者判定を行うService側で設定します。
     */
    @Column(name = "judged_at", nullable = false)
    private LocalDateTime judgedAt;
}