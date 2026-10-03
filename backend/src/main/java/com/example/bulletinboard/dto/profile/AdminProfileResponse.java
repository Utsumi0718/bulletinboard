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
                profile.getUser().getUsername(), profile.getUser().getAccountStatus(),
                profile.getIcon(), profile.getIcon() == null, profile.getBio(),
                profile.getCreatedAt(), profile.getUpdatedAt());
    }
}
