package com.example.bulletinboard.service;

import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.bulletinboard.dto.profile.ProfileResponse;
import com.example.bulletinboard.exception.ProfileOperationException;
import com.example.bulletinboard.exception.ProfileOperationException.Reason;
import com.example.bulletinboard.model.AccountStatus;
import com.example.bulletinboard.model.Profile;
import com.example.bulletinboard.model.ProfileImage;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.ProfileImageRepository;
import com.example.bulletinboard.repository.ProfileRepository;
import com.example.bulletinboard.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

@Service
public class ProfileImageService {
    public static final String URL_PREFIX = "/api/profile-images/";
    private static final Pattern ID = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private final UserRepository users;
    private final ProfileRepository profiles;
    private final ProfileImageRepository images;
    private final EntityManager entityManager;

    public ProfileImageService(UserRepository users, ProfileRepository profiles, ProfileImageRepository images,
            EntityManager entityManager) {
        this.users = users; this.profiles = profiles; this.images = images;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public ProfileResponse detail(Long profileId, String email) {
        User viewer = active(email);
        Profile profile = profiles.findWithUserById(profileId)
                .filter(p -> p.getUser().getAccountStatus() != AccountStatus.WITHDRAWN)
                .orElseThrow(() -> new ProfileOperationException(Reason.NOT_FOUND));
        return ProfileResponse.from(profile, viewer);
    }

    /** 原本保存と利用中参照の変更を同じトランザクションにする。本人以外のIDを受け取らない。 */
    @Transactional
    public ProfileResponse put(TopicImageValidator.ValidatedImage image, String email) {
        User viewer = active(email);
        Profile profile = lockOwnProfile(viewer);
        ProfileImage source = images.saveAndFlush(new ProfileImage(viewer.getId(), image.contentType(), image.bytes()));
        profile.setIcon(URL_PREFIX + source.getId());
        profiles.saveAndFlush(profile);
        return ProfileResponse.from(profile, viewer);
    }

    @Transactional
    public void reset(String email) {
        Profile profile = lockOwnProfile(active(email));
        if (profile.getIcon() != null) {
            profile.setIcon(null);
            profiles.saveAndFlush(profile);
        }
    }

    private Profile lockOwnProfile(User viewer) {
        Profile profile = profiles.findByUserIdForUpdate(viewer.getId())
                .orElseThrow(() -> new ProfileOperationException(Reason.NOT_FOUND));
        // Profile→Userの順でロックし、退会済みになっていないことを更新直前にも確認する。
        User locked = users.findByIdForUpdate(viewer.getId())
                .orElseThrow(() -> new ProfileOperationException(Reason.FORBIDDEN));
        // 最初の認証確認で読み込んだ管理中Entityを、そのまま最新状態だと判断しない。
        entityManager.refresh(locked, LockModeType.PESSIMISTIC_WRITE);
        if (locked.getAccountStatus() != AccountStatus.ACTIVE) throw new ProfileOperationException(Reason.FORBIDDEN);
        return profile;
    }

    /** 通常画像は現在の公開ProfileまたはACTIVEな所有者だけ。証拠テーブルにはアクセスしない。 */
    @Transactional(readOnly = true)
    public ProfileImage get(String id, String email) {
        User viewer = active(email);
        if (!ID.matcher(id).matches()) throw new ProfileOperationException(Reason.NOT_FOUND);
        ProfileImage source = images.findById(id)
                .orElseThrow(() -> new ProfileOperationException(Reason.NOT_FOUND));
        if (!source.getOwnerUserId().equals(viewer.getId())
                && !profiles.existsVisibleIcon(URL_PREFIX + id, AccountStatus.WITHDRAWN)) {
            throw new ProfileOperationException(Reason.NOT_FOUND);
        }
        return source;
    }

    /** 通報対象の所有者がFROZENでも照合可能。URLを取得せず、DB上の所有者と原本を照合する。 */
    @Transactional(readOnly = true)
    public Optional<ProfileImage> findOwnedSource(String url, Long ownerId) {
        if (url == null || !url.startsWith(URL_PREFIX)) return Optional.empty();
        String id = url.substring(URL_PREFIX.length());
        return ID.matcher(id).matches() ? images.findByIdAndOwnerUserId(id, ownerId) : Optional.empty();
    }

    private User active(String email) {
        return users.findByEmail(email).filter(u -> u.getAccountStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new ProfileOperationException(Reason.FORBIDDEN));
    }
}
