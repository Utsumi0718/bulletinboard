package com.example.bulletinboard.model;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="withdrawal_tasks") @Getter @Setter
public class WithdrawalTask {
    @Id @Column(name="user_id") private Long userId;
    @Column(name="session_status", nullable=false, length=30) private String sessionStatus="PENDING";
    @Column(name="image_status", nullable=false, length=30) private String imageStatus="POLICY_PENDING";
    @Column(name="session_attempts", nullable=false) private int sessionAttempts;
    @Column(name="image_attempts", nullable=false) private int imageAttempts;
    @Column(name="last_error_type", length=100) private String lastErrorType;
    @Column(name="created_at", nullable=false) private LocalDateTime createdAt=LocalDateTime.now(java.time.ZoneOffset.UTC);
    @Column(name="updated_at", nullable=false) private LocalDateTime updatedAt=LocalDateTime.now(java.time.ZoneOffset.UTC);
}
