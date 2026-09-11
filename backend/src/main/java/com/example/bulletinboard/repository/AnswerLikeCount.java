package com.example.bulletinboard.repository;

/**
 * 【インターフェースの役割】
 * AnswerごとのLike件数を
 * 一括取得するためのProjectionです。
 *
 * LikeRepositoryの集計Queryから、
 *
 * - Answer ID
 * - Like件数
 *
 * のみを受け取ります。
 *
 * Answer一覧表示時に、
 * Answerごとにcountクエリを実行することを避け、
 * Like件数をまとめて取得するために使用します。
 */
public interface AnswerLikeCount {

    Long getAnswerId();

    Long getLikeCount();
}