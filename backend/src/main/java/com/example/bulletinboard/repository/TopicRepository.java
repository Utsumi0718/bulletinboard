package com.example.bulletinboard.repository;

import java.util.Optional;

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
 * 【主な役割】
 * - Topicの保存
 * - TopicのIDによる取得
 * - 削除されていないTopic一覧の取得
 * - お題タイトルの部分一致検索
 * - ページネーションに対応した一覧取得
 *
 * 【設計上のポイント】
 * - 検索対象はお題タイトルのみです。
 * - 検索方式は部分一致のみです。
 * - deletedAtを利用した論理削除方式を採用しているため、
 *   通常の一覧・詳細・検索では削除済みTopicを除外します。
 */
@Repository
public interface TopicRepository extends JpaRepository<Topic, Long> {

    /*
     * 削除されていないTopicを一覧取得します。
     */
    Page<Topic> findByDeletedAtIsNull(Pageable pageable);

    /*
     * 指定されたIDかつ削除されていないTopicを取得します。
     */
    Optional<Topic> findByIdAndDeletedAtIsNull(Long id);

    /*
     * 削除されていないTopicを対象に、
     * タイトルを部分一致検索します。
     */
    Page<Topic> findByTitleContainingAndDeletedAtIsNull(
        String title,
        Pageable pageable
    );
}