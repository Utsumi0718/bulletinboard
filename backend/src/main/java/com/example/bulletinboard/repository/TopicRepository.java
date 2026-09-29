package com.example.bulletinboard.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.bulletinboard.model.RankingCheckpoint;
import com.example.bulletinboard.model.Topic;
import jakarta.persistence.LockModeType;

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
 * - 自動ランキング判定対象Topicの取得
 *
 * 【設計上のポイント】
 * - 検索対象はお題タイトルのみです。
 * - 検索方式は部分一致のみです。
 * - deletedAtを利用した論理削除方式を採用しているため、
 *   通常の一覧・詳細・検索では削除済みTopicを除外します。
 * - 自動ランキング判定では、
 *   checkpointの基準時刻を迎えており、
 *   まだそのcheckpointを判定していないTopicのみ取得します。
 */
@Repository
public interface TopicRepository extends JpaRepository<Topic, Long> {
    Page<Topic> findByUserId(Long userId, Pageable pageable);

    boolean existsByImageAndDeletedAtIsNull(String image);

    /*
     * 削除されていないTopicを一覧取得します。
     */
    Page<Topic> findByDeletedAtIsNull(Pageable pageable);

    /*
     * 指定されたIDかつ削除されていないTopicを取得します。
     */
    Optional<Topic> findByIdAndDeletedAtIsNull(Long id);

    /** 通報受付と編集・削除が同じお題行を直列に処理する。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Topic t where t.id = :id and t.deletedAt is null")
    Optional<Topic> findReportableByIdForUpdate(@Param("id") Long id);

    /*
     * 削除されていないTopicを対象に、
     * タイトルを部分一致検索します。
     */
    Page<Topic> findByTitleContainingAndDeletedAtIsNull(
        String title,
        Pageable pageable
    );

  /*
   * 自動ランキング判定の対象となるTopicを取得します。
   *
   * 以下の条件をすべて満たすTopicを対象とします。
   *
   * - 論理削除されていない
   * - createdAtが指定された基準時刻以前
   * - 指定されたcheckpointのRankingJudgmentがまだ存在しない
   *
   * Topic.createdAtの古い順に取得します。
   *
   * thresholdには、
   * DAY_7なら現在時刻の7日前、
   * DAY_14なら14日前、
   * DAY_21なら21日前の日時を指定します。
  */
   @Query("""
      SELECT t
      FROM Topic t
      WHERE t.deletedAt IS NULL
        AND t.createdAt <= :threshold
        AND NOT EXISTS (
          SELECT rj.id
          FROM RankingJudgment rj
          WHERE rj.topic = t
            AND rj.checkpoint = :checkpoint
       )
         ORDER BY t.createdAt ASC
       """)
     List<Topic> findTopicsDueForRanking(
        @Param("threshold") LocalDateTime threshold,
        @Param("checkpoint") RankingCheckpoint checkpoint
   );
}
