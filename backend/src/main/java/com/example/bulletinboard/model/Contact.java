package com.example.bulletinboard.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/*
 * 【クラス全体の役割】
 * データベースの「contacts」テーブルと対応するJPAエンティティクラスです。
 *
 * このクラスでは、ユーザーまたは未ログイン利用者から送信された
 * お問い合わせ情報を管理します。
 *
 * 送信者の氏名、メールアドレス、件名、本文のほか、
 * 管理者が対応状況を確認するためのステータス、
 * お問い合わせの作成日時・更新日時を保持します。
 *
 * 【主な役割】
 * - お問い合わせ送信者の氏名を保持する
 * - 連絡先メールアドレスを保持する
 * - お問い合わせの件名を保持する
 * - お問い合わせ本文を保持する
 * - 管理者による対応状況を保持する
 * - お問い合わせの作成日時・更新日時を管理する
 *
 * 【設計上のポイント】
 * - お問い合わせはログインユーザー・未ログインユーザーの
 *   どちらからでも送信できる想定です。
 * - usersテーブルとは紐付けず、送信時に入力された
 *   nameとemailをそのまま保持します。
 * - emailにはUNIQUE制約を付けません。
 *   同じメールアドレスから複数回お問い合わせできます。
 * - statusでは管理者による対応状況を管理します。
 * - statusの初期値はUNANSWEREDです。
 * - statusは現段階ではStringとして保持し、
 *   後ほどEnumとして定義する予定です。
 */
@Entity
@Getter
@Setter
@Table(name = "contacts")
public class Contact {

    /*
     * お問い合わせID。
     * contactsテーブルの主キーで、
     * MySQLのAUTO_INCREMENTによって自動採番されます。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * お問い合わせ送信者の氏名。
     */
    @NotBlank
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /*
     * お問い合わせ送信者のメールアドレス。
     *
     * 同じメールアドレスから複数のお問い合わせを
     * 送信できるためUNIQUE制約は設定しません。
     */
    @NotBlank
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    /*
     * お問い合わせの件名。
     */
    @NotBlank
    @Column(name = "subject", nullable = false, length = 150)
    private String subject;

    /*
     * お問い合わせ本文。
     *
     * DBではTEXT型として保存します。
     */
    @NotBlank
    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    /*
     * 管理者による対応状況。
     *
     * 想定する値：
     * UNANSWERED  : 未対応
     * IN_PROGRESS : 対応中
     * RESOLVED    : 対応済み
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "UNANSWERED";

    /*
     * お問い合わせが作成された日時。
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /*
     * お問い合わせ情報が最後に更新された日時。
     *
     * 主に管理者がstatusを変更した場合などに更新されます。
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /*
     * Contactを初めてDBへ保存する直前に呼び出されます。
     *
     * statusが未設定の場合はUNANSWEREDを設定し、
     * createdAtとupdatedAtに現在日時を設定します。
     */
    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();

        if (status == null) {
            status = "UNANSWERED";
        }

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    /*
     * Contactを更新する直前に呼び出され、
     * updatedAtを現在日時に更新します。
     */
    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}