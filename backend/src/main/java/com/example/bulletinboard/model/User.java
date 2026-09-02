package com.example.bulletinboard.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/*
 * 【クラス全体の役割】
 * データベースの「users」テーブルと対応するJPAエンティティクラスです。
 *
 * このクラスでは、ユーザーを一意に識別するIDのほか、
 * ユーザー名・メールアドレス・ハッシュ化されたパスワードなどの認証情報、
 * ユーザー権限、ログイン失敗回数、ログインロック状態、
 * アカウント状態（通常・凍結・退会）などを管理します。
 *
 * 【主な役割】
 * - ユーザー名、メールアドレス、パスワードなどの基本情報を保持する
 * - ログイン認証に必要な情報を保持する
 * - ROLE_USER / ROLE_ADMIN などのユーザー権限を管理する
 * - ログイン失敗回数と一時的なアカウントロック状態を管理する
 * - ACTIVE / FROZEN / WITHDRAWN などのアカウント状態を管理する
 * - ユーザー作成日時と退会日時を保持する
 *
 * 【設計上のポイント】
 * - username と email は重複を許可しません。
 * - password には平文ではなく、ハッシュ化済みのパスワードを保存します。
 * - accountNonLocked はログイン失敗によるセキュリティロックを表します。
 * - accountStatus は運営による凍結やユーザー自身の退会状態を表します。
 * - accountNonLocked と accountStatus は役割が異なるため、別々に管理します。
 * - ユーザー退会時も関連する投稿・回答・ランキング履歴などを
 *   一括削除しない設計とするため、User側では CascadeType.ALL や
 *   orphanRemoval=true を使用しません。
 * - Topic、Answer、Likeなどとのリレーションは、それぞれのEntity側から
 *   Userを参照する形を基本とし、必要になった場合のみUser側への
 *   双方向リレーション追加を検討します。
 */
@Entity
@Getter
@Setter
@Table(name = "users")
public class User {

    /*
     * ユーザーID。
     * usersテーブルの主キーで、MySQLのAUTO_INCREMENTによって自動採番されます。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * ユーザー名。
     * サービス上で公開されるユーザー識別名です。
     * 重複は許可しません。
     */
    @NotNull
    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    /*
     * メールアドレス。
     * ログインやアカウント管理に使用します。
     * 他ユーザーとの重複は許可しません。
     */
    @NotNull
    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    /*
     * ハッシュ化されたパスワード。
     * 平文パスワードは保存しません。
     */
    @NotNull
    @Column(name = "password", nullable = false, length = 255)
    private String password;

    /*
     * ユーザー権限。
     * 通常ユーザーはROLE_USER、管理者はROLE_ADMINを想定しています。
     */
    @NotNull
    @Column(name = "role", nullable = false, length = 50)
    private String role = "ROLE_USER";

    /*
     * ログイン失敗回数。
     * 一定回数失敗した場合にaccountNonLockedをfalseにして
     * ログインを一時的に制限するために使用します。
     */
    @Column(name = "failed_attempt", nullable = false)
    private int failedAttempt = 0;

    /*
     * ログイン失敗によるアカウントロック状態。
     *
     * true  : ロックされていない
     * false : ロックされている
     *
     * 運営による凍結や退会状態とは別の概念です。
     */
    @Column(name = "account_non_locked", nullable = false)
    private boolean accountNonLocked = true;

    /*
 * アカウントの利用状態。
 *
 * ACTIVE    : 通常利用中
 * FROZEN    : 管理者によって凍結
 * WITHDRAWN : 退会済み
 *
 * AccountStatus列挙型として管理し、
 * DBにはEnumType.STRINGによって文字列で保存します。
 *
 * 管理者による凍結やユーザー自身の退会状態を表し、
 * ログイン失敗によるaccountNonLockedとは別に管理します。
 */
@NotNull
@Enumerated(EnumType.STRING)
@Column(name = "account_status", nullable = false, length = 20)
private AccountStatus accountStatus = AccountStatus.ACTIVE;
    /*
     * ユーザーアカウントの作成日時。
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /*
     * 退会日時。
     * 通常利用中はNULLで、退会処理を行った場合のみ日時を保存します。
     */
    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    /*
     * 新規ユーザーをDBへ保存する直前に呼び出され、
     * createdAtが設定されていなければ現在日時を設定します。
     */
    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}