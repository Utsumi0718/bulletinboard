package com.example.bulletinboard.model;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 値のコピーを含めない、専用権限の閲覧・変更記録。 */
@Entity @Table(name="privacy_audit_events") @Getter @NoArgsConstructor
public class PrivacyAuditEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="actor_user_id", nullable=false) private Long actorUserId;
    @Column(name="target_type", nullable=false, length=30) private String targetType;
    @Column(name="target_id", nullable=false, length=36) private String targetId;
    @Column(nullable=false, length=30) private String action;
    @Column(name="created_at", nullable=false) private LocalDateTime createdAt;
    public PrivacyAuditEvent(Long actor, String type, String target, String action) {
        this.actorUserId=actor; this.targetType=type; this.targetId=target;
        this.action=action; this.createdAt=LocalDateTime.now(java.time.ZoneOffset.UTC);
    }
}
