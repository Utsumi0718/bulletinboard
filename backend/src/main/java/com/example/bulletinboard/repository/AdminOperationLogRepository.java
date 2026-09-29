package com.example.bulletinboard.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

import com.example.bulletinboard.model.AdminOperationLog;

/**
 * 【クラスの役割】
 * 管理者の操作履歴をDBへ保存・取得するRepositoryです。
 *
 * 【設計上のポイント】
 * - お問い合わせ・通報の管理Serviceから、管理操作と同じトランザクションで
 *   操作履歴を保存するために使用します。
 * - 保存・IDによる取得などはJpaRepositoryの機能を利用します。
 * - 操作履歴の更新・削除機能は公開しません。
 *   JpaRepositoryには更新・削除メソッドもありますが、
 *   今回の業務処理からは使用しません。
 */
@Repository
public interface AdminOperationLogRepository
        extends JpaRepository<AdminOperationLog, Long> {
    List<AdminOperationLog> findByTargetTypeAndTargetIdOrderByCreatedAtAscIdAsc(
            String targetType, Long targetId);
}
