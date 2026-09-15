package com.example.bulletinboard.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.bulletinboard.model.RankingJudgment;
import com.example.bulletinboard.model.RankingResult;

/*
 * 【クラス全体の役割】
 * RankingResultのデータベース操作を担当するRepositoryです。
 *
 * RankingResultは、
 * 1回のRankingJudgmentで王者になったAnswerを
 * 履歴として保存するEntityです。
 *
 * このRepositoryでは、
 * RankingResultの登録・取得などのDB操作を担当します。
 *
 * 「誰を王者とするか」
 * 「王者なしと判断するか」
 * といった業務上の判断はRepositoryでは行わず、
 * Service側で行います。
 */
public interface RankingResultRepository
        extends JpaRepository<RankingResult, Long> {

    /*
     * 指定したRankingJudgmentに紐づく
     * RankingResultをすべて取得します。
     *
     * 1つのRankingJudgmentに対して、
     * 同率1位のAnswerが複数存在する場合は、
     * 複数のRankingResultが保存されます。
     *
     * そのため戻り値にはListを使用します。
     *
     * 王者なしの場合
     * → 空のList
     *
     * 王者が1Answerの場合
     * → 要素1件のList
     *
     * 同率王者が複数Answerの場合
     * → 複数件のRankingResultを含むList
     *
     * RankingResultが0件だった場合に
     * 「判定済み・王者なし」と判断するのは
     * Service側の役割です。
     */
    List<RankingResult> findByRankingJudgment(
            RankingJudgment rankingJudgment
    );
}