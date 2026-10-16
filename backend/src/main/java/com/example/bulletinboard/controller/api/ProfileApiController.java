package com.example.bulletinboard.controller.api;

import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.example.bulletinboard.dto.profile.ProfileResponse;
import com.example.bulletinboard.exception.ProfileOperationException;
import com.example.bulletinboard.exception.ProfileOperationException.Reason;
import com.example.bulletinboard.service.ProfileImageService;
import com.example.bulletinboard.service.ProfileImageValidator;

/** SecurityConfigの認証・CSRFを維持する。画像更新は画面の保存操作時だけ呼ぶ。 */
@RestController
public class ProfileApiController {
    private final ProfileImageService profiles;
    private final ProfileImageValidator validator;
    public ProfileApiController(ProfileImageService profiles, ProfileImageValidator validator) {
        this.profiles = profiles; this.validator = validator;
    }

    @GetMapping("/api/profiles/{profileId}")
    public ResponseEntity<ProfileResponse> detail(@PathVariable Long profileId, Authentication authentication) {
        return perform(() -> ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(profiles.detail(profileId, authentication.getName())));
    }

    @PutMapping(value = "/api/profiles/me/icon", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProfileResponse> put(@RequestPart("file") MultipartFile file, Authentication authentication) {
        return perform(() -> ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(profiles.put(validator.validate(file), authentication.getName())));
    }

    @DeleteMapping("/api/profiles/me/icon")
    public ResponseEntity<Void> reset(Authentication authentication) {
        return perform(() -> {
            profiles.reset(authentication.getName());
            return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
        });
    }

    @GetMapping("/api/profile-images/{id}")
    public ResponseEntity<byte[]> image(@PathVariable String id, Authentication authentication) {
        return perform(() -> {
            var image = profiles.get(id, authentication.getName());
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .contentType(MediaType.parseMediaType(image.getContentType())).contentLength(image.getByteSize())
                    .header("X-Content-Type-Options", "nosniff")
                    .header("Content-Security-Policy", "default-src 'none'; sandbox").body(image.getContent());
        });
    }

    private <T> T perform(Supplier<T> action) {
        try { return action.get(); }
        catch (DataAccessException | TransactionException ex) { throw new ProfileOperationException(Reason.FAILED, ex); }
    }
}
