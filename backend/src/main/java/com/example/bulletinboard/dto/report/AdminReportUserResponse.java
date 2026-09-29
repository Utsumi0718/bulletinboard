package com.example.bulletinboard.dto.report;

import com.example.bulletinboard.model.AccountStatus;
import com.example.bulletinboard.model.User;

/** 管理者だけに返す通報者・対象所有者情報。 */
public record AdminReportUserResponse(Long id, String username, String email, AccountStatus accountStatus) {
    public static AdminReportUserResponse from(User user) {
        return new AdminReportUserResponse(user.getId(), user.getUsername(), user.getEmail(),
                user.getAccountStatus());
    }
}
