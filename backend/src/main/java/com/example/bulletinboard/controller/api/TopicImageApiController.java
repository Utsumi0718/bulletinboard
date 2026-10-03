package com.example.bulletinboard.controller.api;

import java.net.URI;

import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.bulletinboard.dto.topic.TopicImageResponse;
import com.example.bulletinboard.exception.TopicImageException;
import com.example.bulletinboard.exception.TopicImageException.Reason;
import com.example.bulletinboard.service.TopicImageService;
import com.example.bulletinboard.service.TopicImageValidator;

/** SecurityConfigの認証・CSRF保護を使用する。公開staticディレクトリへは保存しない。 */
@RestController
@RequestMapping("/api/topic-images")
public class TopicImageApiController {
    private final TopicImageService service;
    private final TopicImageValidator validator;

    public TopicImageApiController(TopicImageService service, TopicImageValidator validator) {
        this.service = service;
        this.validator = validator;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TopicImageResponse> upload(@RequestPart("file") MultipartFile file,
            Authentication authentication) {
        try {
            var result = service.upload(validator.validate(file), authentication.getName());
            // Serviceのトランザクションが終了してから201を返す。
            return ResponseEntity.created(URI.create(result.url())).cacheControl(CacheControl.noStore()).body(result);
        } catch (DataAccessException | TransactionException ex) {
            throw new TopicImageException(Reason.FAILED, ex);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> get(@PathVariable String id, Authentication authentication) {
        try {
            var image = service.get(id, authentication.getName());
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .contentType(MediaType.parseMediaType(image.getContentType()))
                    .contentLength(image.getByteSize())
                    .header("X-Content-Type-Options", "nosniff")
                    .header("Content-Security-Policy", "default-src 'none'; sandbox")
                    .body(image.getContent());
        } catch (DataAccessException | TransactionException ex) {
            throw new TopicImageException(Reason.FAILED, ex);
        }
    }
}
