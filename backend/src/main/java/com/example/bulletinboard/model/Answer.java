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
 * データベースの「answers」テーブルと対応するJPAエンティティクラスです。
 *
 * このクラスでは、大喜利のお題に対してユーザーが投稿する「回答」を管理します。
 * 回答本文、回答したユーザー、対象のお題、作成日時・更新日時、
 * 論理削除用の削除日時などを保持します。
 *
 * 【主な役割】
 * - 大喜利のお題に対する回答本文を保持する
 * - 回答を投稿したユーザーと紐付ける
 * - 回答対象のTopicと紐付ける
 * - 回答の作成日時・更新日時を管理する
 * - 論理削除のための削除日時を管理する
 *
 * 【設計上のポイント】
 * - AnswerはTopicと多対1の関係です。
 *   1つのお題に対して複数の回答を投稿できます。
 * - AnswerはUserとも多対1の関係です。
 *   1人のユーザーが複数の回答を投稿できます。
 * - 同じユーザーが同じお題に複数回答することも許可するため、
 *   user_idとtopic_idにUNIQUE制約は設定しません。
 * - contentはDB上では最大200文字まで保存可能ですが、
 *   アプリ側では最大100文字として入力制限を行う予定です。
 * - 回答を削除する場合は物理削除せず、
 *   deletedAtに削除日時を設定する論理削除方式を採用します。
 * - Likeやランキング履歴などを誤って連鎖削除しないため、
 *   CascadeType.ALLやorphanRemoval=trueは使用しません。
 * - Likeとのリレーションは必要なEntity側からAnswerを参照する
 *   単方向設計を基本とします。
 */
@Entity
@Getter
@Setter
@Table(name = "answers")
public class Answer {

    /*
     * 回答ID。
     * answersテーブルの主キーで、
     * MySQLのAUTO_INCREMENTによって自動採番されます。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * 回答対象のお題。
     *
     * 1つのTopicには複数のAnswerが存在できるため、
     * AnswerからTopicへの多対1の関係として定義します。
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    /*
     * 回答を投稿したユーザー。
     *
     * 1人のユーザーは複数の回答を投稿できるため、
     * AnswerからUserへの多対1の関係として定義します。
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /*
     * 回答本文。
     *
     * DB上では最大200文字まで保存可能です。
     * 実際の入力制限はアプリ側で最大100文字とする予定です。
     */
    @NotBlank
    @Column(name = "content", nullable = false, length = 200)
    private String content;

    /*
     * 回答の作成日時。
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /*
     * 回答の最終更新日時。
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /*
     * 回答の削除日時。
     *
     * NULLの場合は通常の回答、
     * 日時が設定されている場合は削除済みとして扱います。
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /*
     * Answerを初めてDBへ保存する直前に呼び出され、
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
     * Answerを更新する直前に呼び出され、
     * updatedAtを現在日時に更新します。
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}