package com.example.bulletinboard.dto.user;

import java.time.LocalDateTime;

import com.example.bulletinboard.model.AccountStatus;
import com.example.bulletinboard.model.User;

/** 管理者向けUser情報。パスワードは返さない。 */
public record AdminUserResponse(Long id, String username, String email, String role,
        AccountStatus accountStatus, boolean accountNonLocked, int failedAttempt,
        LocalDateTime createdAt, LocalDateTime withdrawnAt) {
    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(user.getId(), com.example.bulletinboard.service.WithdrawalIdentity.publicName(user), com.example.bulletinboard.service.WithdrawalIdentity.publicEmail(user), user.getRole(),
                user.getAccountStatus(), user.isAccountNonLocked(), user.getFailedAttempt(),
                user.getCreatedAt(), user.getWithdrawnAt());
    }
}
