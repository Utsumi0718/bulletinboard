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
 * データベースの「x_post_history」テーブルと対応するJPAエンティティクラスです。
 *
 * このクラスでは、大喜利のお題で王者になった回答を
 * 公式Xアカウントへ自動投稿する処理の履歴を管理します。
 *
 * 投稿対象となったユーザー・お題・回答のほか、
 * Xへの投稿処理の状態、投稿成功時に取得したX側の投稿ID、
 * 投稿失敗時のエラーメッセージ、作成日時・更新日時などを保持します。
 *
 * 【主な役割】
 * - Xへ投稿する対象ユーザーを保持する
 * - Xへ投稿する対象のお題を保持する
 * - Xへ投稿する対象回答を保持する
 * - X投稿処理の状態を保持する
 * - 投稿成功時のX側投稿IDを保持する
 * - 投稿失敗時のエラー内容を保持する
 * - 投稿履歴の作成日時・更新日時を管理する
 *
 * 【設計上のポイント】
 * - user_idとtopic_idの組み合わせにはUNIQUE制約を設定し、
 *   同じユーザーについて同じお題の王者投稿が
 *   重複して実行されることを防ぎます。
 * - statusでは投稿処理の状態を管理します。
 * - 想定するstatusはPENDING / SUCCESS / FAILEDです。
 * - 投稿処理が失敗してもランキング結果や王者実績は取り消さず、
 *   X投稿履歴だけFAILEDとして保持します。
 * - FAILEDとなった履歴は、後ほど再試行できる設計を想定しています。
 * - xPostIdにはXへの投稿成功時に取得した投稿IDを保存します。
 * - errorMessageには投稿失敗時のエラー情報を保存します。
 * - statusは現段階ではStringとして保持し、
 *   後ほどEnumとして定義する予定です。
 * - User、Topic、Answerの削除に連動して履歴を削除しないため、
 *   CascadeType.ALLやorphanRemoval=trueは使用しません。
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
    name = "x_post_history",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_x_post_history_user_topic",
            columnNames = {"user_id", "topic_id"}
        )
    }
)
public class XPostHistory {

    /*
     * X投稿履歴ID。
     * x_post_historyテーブルの主キーで、
     * MySQLのAUTO_INCREMENTによって自動採番されます。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * 王者となりX投稿対象になったユーザー。
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /*
     * X投稿対象となったお題。
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    /*
     * X投稿対象となった王者回答。
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answer_id", nullable = false)
    private Answer answer;

    /*
     * X投稿処理の状態。
     *
     * 想定する値：
     * PENDING : 投稿待ち・処理中
     * SUCCESS : 投稿成功
     * FAILED  : 投稿失敗
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    /*
     * Xへの投稿成功時に取得した投稿ID。
     *
     * 投稿前または投稿失敗時はNULLです。
     */
    @Column(name = "x_post_id", length = 100)
    private String xPostId;

    /*
     * Xへの投稿処理が失敗した場合のエラーメッセージ。
     *
     * 投稿成功時はNULLを想定しています。
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /*
     * X投稿履歴が作成された日時。
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /*
     * X投稿履歴が最後に更新された日時。
     *
     * 投稿成功・失敗・再試行などによって状態が変わった際に更新されます。
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /*
     * XPostHistoryを初めてDBへ保存する直前に呼び出されます。
     *
     * statusが未設定の場合はPENDINGを設定し、
     * createdAtとupdatedAtに現在日時を設定します。
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();

        if (status == null) {
            status = "PENDING";
        }

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    /*
     * XPostHistoryを更新する直前に呼び出され、
     * updatedAtを現在日時に更新します。
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}