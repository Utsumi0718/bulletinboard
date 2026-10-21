package com.example.bulletinboard.model;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 通常のUser・管理DTOから分離する保持記録。未確定の期限を補完しない。 */
@Entity @Table(name = "privacy_retention_records") @Getter @Setter
public class PrivacyRetentionRecord {
    public enum Target { USER_IDENTITY, PROFILE_IMAGE, REPORT_EVIDENCE }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="user_id", nullable=false) private Long userId;
    @Enumerated(EnumType.STRING) @Column(name="target_type", nullable=false, length=30) private Target targetType;
    @Column(name="target_id", nullable=false, length=36) private String targetId;
    @Column(name="report_id") private Long reportId;
    @Column(length=500) private String purpose;
    @Column(length=500) private String basis;
    @Column(name="expires_at") private LocalDateTime expiresAt;
    @Column(name="access_scope", nullable=false, length=30) private String accessScope="PRIVACY_REVIEWER";
    @Column(nullable=false, length=30) private String status="POLICY_PENDING";
    @Column(name="identity_fields", length=50) private String identityFields;
    @Lob @Column(name="encrypted_payload", columnDefinition="MEDIUMBLOB") private byte[] encryptedPayload;
    @Column(name="key_id", length=80) private String keyId;
    @Column(name="created_by") private Long createdBy;
    @Column(name="created_at", nullable=false) private LocalDateTime createdAt=LocalDateTime.now(java.time.ZoneOffset.UTC);
    @Column(name="updated_at", nullable=false) private LocalDateTime updatedAt=LocalDateTime.now(java.time.ZoneOffset.UTC);
    public boolean holdsAt(LocalDateTime now) {
        return "POLICY_PENDING".equals(status) || ("ACTIVE".equals(status) && expiresAt != null && expiresAt.isAfter(now));
    }
}
