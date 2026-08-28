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
 * - お題タイトルの部分一致検索
 * - ページネーションに対応した一覧取得
 *
 * 【設計上のポイント】
 * - 旧PostRepositoryではtitleとcontentを検索対象にしていましたが、
 *   新しいTopicではtitleのみを検索対象とします。
 * - 検索方式は部分一致検索のみを採用します。
 * - お題の削除はdeletedAtを利用した論理削除方式を採用するため、
 *   実際の一覧取得や検索では削除済みTopicを除外する処理を
 *   今後Service層またはRepository側で追加する予定です。
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
}