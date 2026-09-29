package com.example.bulletinboard.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.bulletinboard.dto.profile.AdminProfileResponse;
import com.example.bulletinboard.exception.AdminProfileNotFoundException;
import com.example.bulletinboard.exception.ForbiddenOperationException;
import com.example.bulletinboard.exception.UserNotFoundException;
import com.example.bulletinboard.model.AccountStatus;
import com.example.bulletinboard.model.AdminOperationLog;
import com.example.bulletinboard.model.Profile;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.AdminOperationLogRepository;
import com.example.bulletinboard.repository.ProfileRepository;
import com.example.bulletinboard.repository.UserRepository;

/** 管理者のProfile確認とアイコン・自己紹介の初期化を担当する。 */
@Service
public class AdminProfileService {
    private final ProfileRepository profiles;
    private final UserRepository users;
    private final AdminOperationLogRepository logs;

    public AdminProfileService(ProfileRepository profiles, UserRepository users, AdminOperationLogRepository logs) {
        this.profiles = profiles;
        this.users = users;
        this.logs = logs;
    }

    private User admin(String email) {
        if (email == null || email.isBlank()) throw new UserNotFoundException("ログインユーザー情報を取得できませんでした。");
        User actor = users.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("ログインユーザー情報を取得できませんでした。"));
        if (!"ROLE_ADMIN".equals(actor.getRole()) || actor.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new ForbiddenOperationException("この操作は管理者のみ実行できます。");
        }
        return actor;
    }

    @Transactional(readOnly = true)
    public AdminProfileResponse detail(Long profileId, String email) {
        admin(email);
        return AdminProfileResponse.from(profiles.findById(profileId)
                .orElseThrow(AdminProfileNotFoundException::new));
    }

    @Transactional(readOnly = true)
    public AdminProfileResponse byUser(Long userId, String email) {
        admin(email);
        return AdminProfileResponse.from(profiles.findByUserId(userId)
                .orElseThrow(AdminProfileNotFoundException::new));
    }

    @Transactional
    public void resetIcon(Long profileId, String email) {
        User actor = admin(email);
        Profile profile = profiles.findByIdForUpdate(profileId)
                .orElseThrow(AdminProfileNotFoundException::new);
        if (profile.getIcon() == null) return;
        // Profileの参照だけを外す。原本の保存先は未実装であり、共有参照を削除しない。
        profile.setIcon(null);
        profiles.saveAndFlush(profile);
        logs.saveAndFlush(new AdminOperationLog(actor, "PROFILE", profileId,
                "ICON_RESET", "SET", "UNSET"));
    }

    @Transactional
    public void clearBio(Long profileId, String email) {
        User actor = admin(email);
        Profile profile = profiles.findByIdForUpdate(profileId)
                .orElseThrow(AdminProfileNotFoundException::new);
        if (profile.getBio() == null) return;
        profile.setBio(null);
        profiles.saveAndFlush(profile);
        logs.saveAndFlush(new AdminOperationLog(actor, "PROFILE", profileId,
                "BIO_CLEAR", "SET", "UNSET"));
    }
}
