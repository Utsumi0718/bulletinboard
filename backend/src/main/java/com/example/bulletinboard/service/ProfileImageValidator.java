package com.example.bulletinboard.service;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import com.example.bulletinboard.exception.ProfileOperationException;
import com.example.bulletinboard.exception.ProfileOperationException.Reason;
import com.example.bulletinboard.exception.TopicImageException;

/** No.5の2MBは2,000,000バイト。切り抜き・リサイズ保存は行わない。 */
@Component
public class ProfileImageValidator {
    public static final int MAX_BYTES = 2_000_000;
    private final TopicImageValidator images;
    public ProfileImageValidator(TopicImageValidator images) { this.images = images; }

    public TopicImageValidator.ValidatedImage validate(MultipartFile file) {
        try {
            return images.validate(file, MAX_BYTES);
        } catch (TopicImageException ex) {
            Reason reason = switch (ex.getReason()) {
                case TOO_LARGE -> Reason.TOO_LARGE;
                case FAILED -> Reason.FAILED;
                default -> Reason.INVALID_IMAGE;
            };
            throw new ProfileOperationException(reason, ex);
        }
    }
}
