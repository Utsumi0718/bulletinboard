package com.example.bulletinboard.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.bulletinboard.model.RankingCheckpoint;
import com.example.bulletinboard.model.RankingJudgment;
import com.example.bulletinboard.model.Topic;

/*
 * 【クラス全体の役割】
 * RankingJudgmentのデータベース操作を担当するRepositoryです。
 *
 * RankingJudgmentは、
 * 「どのTopicについて、どのcheckpointで、いつ王者判定を行ったか」
 * という判定履歴を管理するEntityです。
 *
 * このRepositoryでは、
 * RankingJudgmentの登録・取得などのDB操作を担当します。
 *
 * 「判定済みかどうか」
 * 「未判定なら新しく判定するか」
 * といった業務上の判断はRepositoryでは行わず、
 * Service側で行います。
 */
public interface RankingJudgmentRepository
        extends JpaRepository<RankingJudgment, Long> {

    /*
     * 指定したTopicとcheckpointに対応する
     * RankingJudgmentを取得します。
     *
     * 例：
     * Topic A + DAY_7
     * Topic A + DAY_14
     * Topic A + DAY_21
     *
     * DBではtopic_idとcheckpointの組み合わせに
     * UNIQUE制約が設定されているため、
     * 検索結果は0件または1件になります。
     *
     * RankingJudgmentがまだ存在しない場合もあるため、
     * 戻り値にはOptionalを使用します。
     *
     * Optional.empty()だった場合に、
     * 「未判定なので判定処理を進める」
     * と判断するのはService側の役割です。
     */
    Optional<RankingJudgment> findByTopicAndCheckpoint(
            Topic topic,
            RankingCheckpoint checkpoint
    );

       /*
     * 指定したTopicに紐づくRankingJudgmentの中から、
     * judgedAtが最も新しい判定記録を1件取得します。
     *
     * judgedAtを降順（DESC）に並べ、
     * その先頭1件を取得することで
     * 最新のRankingJudgmentを取得します。
     *
     * まだ一度も王者判定が行われていない場合は
     * RankingJudgmentが存在しないため、
     * 戻り値にはOptionalを使用します。
     *
     * 主にTopicの「現在の王者」を取得する際に使用します。
     */
    Optional<RankingJudgment> findFirstByTopicOrderByJudgedAtDesc(
            Topic topic
    );
}