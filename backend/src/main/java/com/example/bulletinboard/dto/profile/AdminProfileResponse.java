package com.example.bulletinboard.dto.profile;

import java.time.LocalDateTime;

import com.example.bulletinboard.model.AccountStatus;
import com.example.bulletinboard.model.Profile;

/** 管理者向けProfile情報。画像原本の有無は保証しない。 */
public record AdminProfileResponse(Long id, Long userId, String username, AccountStatus accountStatus,
        String iconReference, boolean usesDefaultIcon, String bio,
        LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static AdminProfileResponse from(Profile profile) {
        return new AdminProfileResponse(profile.getId(), profile.getUser().getId(),
                com.example.bulletinboard.service.WithdrawalIdentity.publicName(profile.getUser()), profile.getUser().getAccountStatus(),
                profile.getUser().getAccountStatus() == AccountStatus.WITHDRAWN ? null : profile.getIcon(), profile.getUser().getAccountStatus() == AccountStatus.WITHDRAWN || profile.getIcon() == null, profile.getUser().getAccountStatus() == AccountStatus.WITHDRAWN ? null : profile.getBio(),
                profile.getCreatedAt(), profile.getUpdatedAt());
    }
}
