package com.example.bulletinboard.model;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * 【クラスの役割】
 * ユーザーから送信されたお問い合わせ情報をデータベースの `contacts` テーブルとマッピングする Entity クラス。
 * 送信者の氏名、メールアドレス、件名、本文のほか、管理用のステータス（未対応/対応中/完了）および送信日時を保持・管理します。
 */
@Entity
@Table(name = "contacts")
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String email;
    private String subject;
    private String message;

    // ステータス: UNANSWERED(未対応), IN_PROGRESS(対応中), RESOLVED(完了)
    private String status;

    private LocalDateTime createdAt;

    /**
     * エンティティがデータベースに永続化される直前に自動的に呼び出される初期化処理。
     * ステータスが未設定の場合は初期値として "UNANSWERED"（未対応）をセットし、作成日時を設定します。
     */
    @PrePersist
    public void prePersist() {
        if (this.status == null) {
            this.status = "UNANSWERED";
        }
        this.createdAt = LocalDateTime.now();
    }

    // Getter / Setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}