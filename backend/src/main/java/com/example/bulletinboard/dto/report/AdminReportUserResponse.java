package com.example.bulletinboard.dto.report;

import com.example.bulletinboard.model.AccountStatus;
import com.example.bulletinboard.model.User;

/** 管理者だけに返す通報者・対象所有者情報。 */
public record AdminReportUserResponse(Long id, String username, String email, AccountStatus accountStatus) {
    public static AdminReportUserResponse from(User user) {
        return new AdminReportUserResponse(user.getId(), com.example.bulletinboard.service.WithdrawalIdentity.publicName(user), com.example.bulletinboard.service.WithdrawalIdentity.publicEmail(user),
                user.getAccountStatus());
    }
}
