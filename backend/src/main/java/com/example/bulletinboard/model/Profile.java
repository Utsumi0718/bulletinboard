package com.example.bulletinboard.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/*
 * 【クラス全体の役割】
 * データベースの「profiles」テーブルと対応するJPAエンティティクラスです。
 *
 * ユーザーの公開プロフィール情報を管理します。
 * 認証に必要なメールアドレスやパスワードなどはUserエンティティで管理し、
 * Profileではアイコン画像や自己紹介など、
 * サービス上で他のユーザーに表示する情報を保持します。
 *
 * 【主な役割】
 * - Userと1対1で紐付くプロフィール情報を保持する
 * - プロフィールアイコンの保存先情報を保持する
 * - 自己紹介文を保持する
 * - プロフィールの作成日時・更新日時を管理する
 *
 * 【設計上のポイント】
 * - 1人のユーザーにつきプロフィールは1件だけ作成します。
 * - profiles.user_idにはUNIQUE制約があるため、
 *   同じUserに複数のProfileを紐付けることはできません。
 * - Userを削除した際にProfileを自動削除するCascadeType.ALLは使用しません。
 * - iconには画像そのものではなく、
 *   画像ファイルのパスやURLなどの参照情報を保存する想定です。
 * - iconがNULLの場合は、画面側でデフォルトアイコンを表示します。
 * - bioは任意入力です。
 */
@Entity
@Getter
@Setter
@Table(name = "profiles")
public class Profile {

    /*
     * プロフィールID。
     * profilesテーブルの主キーで、
     * MySQLのAUTO_INCREMENTによって自動採番されます。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * このプロフィールを所有するユーザー。
     *
     * UserとProfileは1対1の関係です。
     * profiles.user_idがusers.idを外部キーとして参照します。
     *
     * FetchType.LAZYにすることで、
     * Profile取得時にUser情報が不要な場合は
     * 必要になるまでUserを読み込まないようにします。
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /*
     * プロフィールアイコン。
     *
     * 画像そのものではなく、
     * 保存された画像ファイルのパスやURLなどを保持します。
     *
     * NULLの場合はデフォルトアイコンを表示する想定です。
     */
    @Column(name = "icon", length = 255)
    private String icon;

    /*
     * 自己紹介文。
     *
     * DBでは最大300文字まで保存可能です。
     * 実際の入力文字数制限はアプリ側で200文字とする予定です。
     */
    @Column(name = "bio", length = 300)
    private String bio;

    /*
     * プロフィールの作成日時。
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /*
     * プロフィールの最終更新日時。
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /*
     * Profileを初めてDBへ保存する直前に呼び出されます。
     *
     * createdAtとupdatedAtの両方に現在日時を設定します。
     */
    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    /*
     * Profileを更新する直前に呼び出されます。
     *
     * updatedAtを現在日時に更新します。
     */
    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}