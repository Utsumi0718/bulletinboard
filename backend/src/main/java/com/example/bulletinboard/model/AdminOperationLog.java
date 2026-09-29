package com.example.bulletinboard.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 【クラスの役割】
 * 管理者の操作履歴をadmin_operation_logsテーブルへ保存するEntityです。
 *
 * 【記録する内容】
 * - 操作した管理者
 * - 操作対象の種類とID
 * - 操作内容
 * - 変更前後のステータス
 * - 操作結果
 * - 操作日時
 *
 * 【設計上のポイント】
 * - お問い合わせの状態変更・削除、通報の状態変更と同じトランザクションで保存します。
 * - 名前・メールアドレス・件名・本文は履歴へコピーしません。
 * - 操作対象はIDとして保持し、ContactやReportとのリレーションは持ちません。
 *   これにより、お問い合わせを物理削除した後も履歴を保持します。
 * - 管理者との関連には削除のカスケードを設定しません。
 * - 履歴を通常の処理から書き換えないよう、Setterは用意しません。
 */
@Entity
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@Table(name = "admin_operation_logs")
public class AdminOperationLog {

    /**
     * 操作履歴ID。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 操作した管理者。
     * 管理Serviceで認証情報から特定したUserを設定します。
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "admin_user_id",
            nullable = false,
            updatable = false
    )
    private User adminUser;

    /**
     * 操作対象の種類。お問い合わせはCONTACT、通報はREPORT。
     */
    @Column(
            name = "target_type",
            nullable = false,
            length = 20,
            updatable = false
    )
    private String targetType;

    /**
     * 操作対象のID。
     * 対象削除後も残すため、ContactやReportへの外部キーは持ちません。
     */
    @Column(name = "target_id", nullable = false, updatable = false)
    private Long targetId;

    /**
     * 操作内容。現在はSTATUS_CHANGEまたはDELETE。
     */
    @Column(
            name = "action",
            nullable = false,
            length = 30,
            updatable = false
    )
    private String action;

    /**
     * 変更前、または削除時点のステータス。
     */
    @Column(
            name = "before_status",
            nullable = false,
            length = 20,
            updatable = false
    )
    private String beforeStatus;

    /**
     * 変更後のステータス。削除時はnull。
     */
    @Column(name = "after_status", length = 20, updatable = false)
    private String afterStatus;

    /**
     * 操作結果。このテーブルには成功した操作を記録します。
     */
    @Column(
            name = "result",
            nullable = false,
            length = 20,
            updatable = false
    )
    private String result;

    /**
     * 操作履歴の作成日時。
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 成功した管理操作の履歴を生成します。
     *
     * 生成・保存しただけでは成功は確定せず、
     * 管理操作と履歴保存の両方がコミットされて確定します。
     */
    public AdminOperationLog(
            User adminUser,
            String targetType,
            Long targetId,
            String action,
            String beforeStatus,
            String afterStatus) {

        this.adminUser = adminUser;
        this.targetType = targetType;
        this.targetId = targetId;
        this.action = action;
        this.beforeStatus = beforeStatus;
        this.afterStatus = afterStatus;
        this.result = "SUCCESS";
    }

    /**
     * 新規保存直前に作成日時を設定します。
     */
    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
