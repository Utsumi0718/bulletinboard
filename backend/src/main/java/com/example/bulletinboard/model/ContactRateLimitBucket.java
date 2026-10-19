package com.example.bulletinboard.model;

import java.time.LocalDateTime;
import jakarta.persistence.*;

/** IPのハッシュごとに直近の許可5回だけを保持する。操作は専用ServiceのJDBCで行う。 */
@Entity
@Table(name = "contact_rate_limit_buckets", indexes = @Index(name = "idx_contact_rate_expiry", columnList = "expires_at"))
public class ContactRateLimitBucket {
    @Id @Column(name = "source_key", length = 64) private String sourceKey;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    @Column(name = "slot1") private LocalDateTime slot1;
    @Column(name = "slot2") private LocalDateTime slot2;
    @Column(name = "slot3") private LocalDateTime slot3;
    @Column(name = "slot4") private LocalDateTime slot4;
    @Column(name = "slot5") private LocalDateTime slot5;
}
