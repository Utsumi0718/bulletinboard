package com.example.bulletinboard.dto.profile;

import com.example.bulletinboard.dto.common.PublicDisplayName;
import com.example.bulletinboard.model.Profile;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.service.ReportEligibility;

/** idはprofiles.id。メールや通報者・管理者用証拠の情報を含めない。 */
public record ProfileResponse(Long id, String username, String icon, String bio, boolean canReport) {
    public static ProfileResponse from(Profile profile, User viewer) {
        return new ProfileResponse(profile.getId(), PublicDisplayName.from(profile.getUser()), profile.getIcon(),
                profile.getBio(), ReportEligibility.canReport(profile, viewer));
    }
}
