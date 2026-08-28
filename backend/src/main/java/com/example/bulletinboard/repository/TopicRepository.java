package com.example.bulletinboard.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.bulletinboard.model.Topic;

/*
 * 【クラス（インターフェース）の役割】
 * データベースの「topics」テーブルに対するデータ操作を担当する
 * Repositoryインターフェースです。
 *
 * Topicエンティティを対象として、
 * 保存・取得・更新・削除などの基本的なCRUD操作を行います。
 *
 * JpaRepositoryを継承することで、
 * 基本的なSQLを直接記述せずにデータベース操作を行えます。
 *
 * 【主な役割】
 * - Topicの保存
 * - TopicのIDによる取得
 * - Topic一覧の取得
 * - Topicの更新・削除
 * - お題タイトルを対象とした検索
 * - ページネーションに対応した一覧取得
 *
 * 【設計上のポイント】
 * - 旧PostRepositoryではtitleとcontentを検索対象にしていましたが、
 *   新しいTopicではcontentが存在しないため、
 *   現在はtitleのみを検索対象とします。
 * - お題の削除はdeletedAtを利用した論理削除方式を採用するため、
 *   実際の一覧取得や検索では削除済みTopicを除外する処理を
 *   今後Service層またはRepository側で追加する予定です。
 * - 現在は既存検索機能との移行を考慮し、
 *   部分一致・前方一致・後方一致の検索メソッドを定義しています。
 */
@Repository
public interface TopicRepository extends JpaRepository<Topic, Long> {

    /*
     * タイトルの部分一致検索。
     *
     * 例：
     * keyword = "猫"
     * → 「猫がこちらを見ている」などを検索できます。
     */
    Page<Topic> findByTitleContaining(
        String title,
        Pageable pageable
    );

    /*
     * タイトルの前方一致検索。
     *
     * 指定した文字列から始まるタイトルを検索します。
     */
    Page<Topic> findByTitleStartingWith(
        String title,
        Pageable pageable
    );

    /*
     * タイトルの後方一致検索。
     *
     * 指定した文字列で終わるタイトルを検索します。
     */
    Page<Topic> findByTitleEndingWith(
        String title,
        Pageable pageable
    );
}