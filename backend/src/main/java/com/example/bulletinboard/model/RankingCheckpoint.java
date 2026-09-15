package com.example.bulletinboard.model;

/*
 * ランキング判定で使用するチェックポイントを表す列挙型です。
 *
 * Topicの投稿日時を基準として、
 * 王者判定を行うタイミングを以下の3種類で管理します。
 *
 * DAY_7  ：投稿から7日後の判定
 * DAY_14 ：投稿から14日後の判定
 * DAY_21 ：投稿から21日後の最終判定
 *
 * RankingJudgmentではこのEnumを使用し、
 * checkpointに想定外の文字列が入らないようにします。
 */
public enum RankingCheckpoint {

    DAY_7,
    DAY_14,
    DAY_21
}