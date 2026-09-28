package com.example.bulletinboard.service;

import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.bulletinboard.dto.topic.TopicImageResponse;
import com.example.bulletinboard.exception.TopicImageException;
import com.example.bulletinboard.exception.TopicImageException.Reason;
import com.example.bulletinboard.model.AccountStatus;
import com.example.bulletinboard.model.TopicImage;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.TopicImageRepository;
import com.example.bulletinboard.repository.TopicRepository;
import com.example.bulletinboard.repository.UserRepository;
import com.example.bulletinboard.service.TopicImageValidator.ValidatedImage;

@Service
public class TopicImageService {
    public static final String URL_PREFIX = "/api/topic-images/";
    private static final Pattern ID = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final TopicImageRepository images;
    private final UserRepository users;
    private final TopicRepository topics;

    public TopicImageService(TopicImageRepository images, UserRepository users, TopicRepository topics) {
        this.images = images;
        this.users = users;
        this.topics = topics;
    }

    @Transactional
    public TopicImageResponse upload(ValidatedImage image, String email) {
        User owner = requireActiveUser(email);
        TopicImage saved = images.save(new TopicImage(owner.getId(), image.contentType(), image.bytes()));
        return new TopicImageResponse(URL_PREFIX + saved.getId());
    }

    /** 未使用・差し替え後の画像は所有者のみ。公開中Topicの画像は認証済み利用者が取得できる。 */
    @Transactional(readOnly = true)
    public TopicImage get(String id, String email) {
        User viewer = requireActiveUser(email);
        if (!ID.matcher(id).matches()
                || !(images.existsByIdAndOwnerUserId(id, viewer.getId())
                || topics.existsByImageAndDeletedAtIsNull(URL_PREFIX + id))) {
            throw new TopicImageException(Reason.NOT_FOUND);
        }
        return images.findById(id).orElseThrow(() -> new TopicImageException(Reason.NOT_FOUND));
    }

    /** 任意URL・パスを取得せず、本人の管理下画像だけをTopicへ設定する。 */
    @Transactional(readOnly = true)
    public void requireOwnedImage(String url, String email) {
        User owner = requireActiveUser(email);
        if (url == null || !url.startsWith(URL_PREFIX)) {
            throw new TopicImageException(Reason.INVALID_REFERENCE);
        }
        String id = url.substring(URL_PREFIX.length());
        if (!ID.matcher(id).matches() || !images.existsByIdAndOwnerUserId(id, owner.getId())) {
            throw new TopicImageException(Reason.INVALID_REFERENCE);
        }
    }

    private User requireActiveUser(String email) {
        User user = users.findByEmail(email).orElseThrow(() -> new TopicImageException(Reason.FORBIDDEN));
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new TopicImageException(Reason.FORBIDDEN);
        }
        return user;
    }
}
