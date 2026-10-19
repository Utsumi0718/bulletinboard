package com.example.bulletinboard.model;

import java.time.LocalDateTime;
import jakarta.persistence.*;

/** 自動削除専用の成功記録。管理者を偽装せず、名前・メール・本文・保持理由を複製しない。 */
@Entity
@Table(name = "contact_retention_logs")
public class ContactRetentionLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "contact_id", nullable = false, updatable = false) private Long contactId;
    @Column(name = "status", nullable = false, length = 20, updatable = false) private String status;
    @Column(name = "deleted_at", nullable = false, updatable = false) private LocalDateTime deletedAt;
    protected ContactRetentionLog() { }
    public ContactRetentionLog(Contact contact, LocalDateTime deletedAt) {
        this.contactId = contact.getId(); this.status = contact.getStatus().name(); this.deletedAt = deletedAt;
    }
}
