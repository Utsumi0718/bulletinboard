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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/*
 * 【クラス全体の役割】
 * データベースの「reports」テーブルと対応するJPAエンティティクラスです。
 *
 * このクラスでは、ユーザーから送信された通報情報を管理します。
 *
 * 通報対象は、お題（Topic）・回答（Answer）・プロフィール（Profile）を想定しており、
 * targetTypeとtargetIdを組み合わせることで、どのデータが通報対象なのかを表現します。
 *
 * 【主な役割】
 * - 通報を行ったユーザーを保持する
 * - 通報対象の種類を保持する
 * - 通報対象のIDを保持する
 * - 通報理由を保持する
 * - 必要に応じて通報の詳細説明を保持する
 * - 管理者による対応状況を保持する
 * - 通報の作成日時・更新日時を管理する
 *
 * 【設計上のポイント】
 * - 通報できる対象はTOPIC / ANSWER / PROFILEを想定しています。
 * - targetIdは通報対象によって参照先テーブルが変わるため、
 *   データベース上の外部キー制約は設定しません。
 * - 通報対象が実際に存在するかどうかはService層で確認します。
 * - 自分自身の投稿・回答・プロフィールを通報できないようにする判定も
 *   Service層で行う予定です。
 * - 同じユーザーが同じ対象を何度も通報できないように、
 *   reporter_user_id、target_type、target_idの組み合わせに
 *   UNIQUE制約を設定します。
 * - reasonには通報理由を表す値を保存します。
 * - reasonがOTHERの場合にはdetailの入力を必須とするなどのルールは、
 *   Service層またはフォームのバリデーションで処理します。
 * - statusでは管理者による対応状況を管理します。
 * - targetType、reason、statusは現段階ではStringとして保持し、
 *   後ほどEnumへ変更する予定です。
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
    name = "reports",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_reports_reporter_target",
            columnNames = {
                "reporter_user_id",
                "target_type",
                "target_id"
            }
        )
    }
)
public class Report {

    /*
     * 通報ID。
     * reportsテーブルの主キーで、
     * MySQLのAUTO_INCREMENTによって自動採番されます。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * 通報を行ったユーザー。
     *
     * 1人のユーザーは複数の通報を行えるため、
     * ReportからUserへの多対1の関係として定義します。
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_user_id", nullable = false)
    private User reporterUser;

    /*
     * 通報対象の種類。
     *
     * 想定する値：
     * TOPIC
     * ANSWER
     * PROFILE
     *
     * targetIdがどのテーブルのIDを示しているのかを判別するために使用します。
     */
    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    /*
     * 通報対象となるデータのID。
     *
     * targetTypeによって参照対象が変わります。
     *
     * 例：
     * targetType = TOPIC   → topics.id
     * targetType = ANSWER  → answers.id
     * targetType = PROFILE → profiles.id
     *
     * 複数のテーブルを参照する可能性があるため、
     * JPAのリレーションやDB外部キーは設定しません。
     */
    @Column(name = "target_id", nullable = false)
    private Long targetId;

    /*
     * 通報理由。
     *
     * 想定する値：
     * ABUSE
     * RIGHTS_VIOLATION
     * INAPPROPRIATE
     * PRIVACY
     * SOLICITATION
     * SPAM
     * OTHER
     */
    @Column(name = "reason", nullable = false, length = 50)
    private String reason;

    /*
     * 通報内容の詳細。
     *
     * 任意入力ですが、
     * reasonがOTHERの場合などはアプリ側で必須入力にする予定です。
     */
    @Column(name = "detail", length = 500)
    private String detail;

    /*
     * 管理者による通報対応状況。
     *
     * 想定する値：
     * UNHANDLED   : 未対応
     * IN_PROGRESS : 対応中
     * RESOLVED    : 対応済み
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "UNHANDLED";

    /*
     * 通報が作成された日時。
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /*
     * 通報情報が最後に更新された日時。
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /*
     * Reportを初めてDBへ保存する直前に呼び出されます。
     *
     * createdAtとupdatedAtが未設定の場合、
     * 現在日時を設定します。
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    /*
     * Reportを更新する直前に呼び出され、
     * updatedAtを現在日時に更新します。
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}