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
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/*
 * 【クラス全体の役割】
 * データベースの「topics」テーブルと対応するJPAエンティティクラスです。
 *
 * このクラスでは、大喜利で使用する「お題」の情報を管理します。
 * お題にはタイトル、画像、問題文、投稿者、作成日時、更新日時などを保持します。
 *
 * 【主な役割】
 * - 大喜利のお題タイトルを保持する
 * - お題で使用する画像の保存先情報を保持する
 * - 写真に対する問題文（お題文）を保持する
 * - お題を投稿したユーザー情報と紐付ける
 * - お題の作成日時・更新日時を管理する
 * - 論理削除のための削除日時を管理する
 *
 * 【設計上のポイント】
 * - TopicはUserと多対1の関係です。
 *   1人のユーザーが複数のお題を投稿できます。
 * - imageには画像そのものではなく、
 *   保存された画像ファイルのパスやURLなどの参照情報を保存する想定です。
 * - titleはDB上では最大100文字ですが、
 *   アプリ側では最大50文字として入力制限を行う予定です。
 * - questionはDB上では最大200文字ですが、
 *   アプリ側では最大100文字として入力制限を行う予定です。
 * - お題を削除する場合は物理削除せず、
 *   deletedAtに削除日時を設定する論理削除方式を採用します。
 * - AnswerやLikeなどの関連データを誤って連鎖削除しないため、
 *   Topic側ではCascadeType.ALLやorphanRemoval=trueを使用しません。
 * - Answerとのリレーションは必要なEntity側から参照する単方向設計を基本とし、
 *   必要になった場合のみTopic側への双方向リレーション追加を検討します。
 */
@Entity
@Getter
@Setter
@Table(name = "topics")
public class Topic {

    /*
     * お題ID。
     * topicsテーブルの主キーで、
     * MySQLのAUTO_INCREMENTによって自動採番されます。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * お題を投稿したユーザー。
     *
     * 1人のユーザーは複数のお題を投稿できるため、
     * TopicからUserへの多対1の関係として定義します。
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /*
     * お題のタイトル。
     *
     * DB上では最大100文字まで保存可能です。
     * 実際の入力制限はアプリ側で最大50文字とする予定です。
     */
    @NotBlank
    @Column(name = "title", nullable = false, length = 100)
    private String title;

    /*
     * お題に使用する画像。
     *
     * 画像そのものではなく、
     * 保存された画像ファイルのパスやURLなどを保持します。
     */
    @NotBlank
    @Column(name = "image", nullable = false, length = 255)
    private String image;

    /*
     * 写真に対する問題文（お題文）。
     *
     * DB上では最大200文字まで保存可能です。
     * 実際の入力制限はアプリ側で最大100文字とする予定です。
     */
    @NotBlank
    @Column(name = "question", nullable = false, length = 200)
    private String question;

    /*
     * お題の作成日時。
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /*
     * お題の最終更新日時。
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /*
     * お題の削除日時。
     *
     * NULLの場合は通常のお題、
     * 日時が設定されている場合は削除済みとして扱います。
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /*
     * Topicを初めてDBへ保存する直前に呼び出され、
     * createdAtとupdatedAtを設定します。
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
     * Topicを更新する直前に呼び出され、
     * updatedAtを現在日時に更新します。
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}