package com.example.bulletinboard.controller.api;

import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.bulletinboard.dto.common.PageResponse;
import com.example.bulletinboard.dto.content.AdminAnswerResponse;
import com.example.bulletinboard.dto.content.AdminContentVisibility;
import com.example.bulletinboard.dto.content.AdminTopicResponse;
import com.example.bulletinboard.exception.AdminContentOperationException;
import com.example.bulletinboard.exception.AdminContentOperationException.Operation;
import com.example.bulletinboard.service.AdminContentService;

/** 通報詳細のtargetType/targetIdからも指定できる管理用Topic/Answer API。 */
@RestController
@RequestMapping("/api/admin")
public class AdminContentApiController {
    private final AdminContentService service;

    public AdminContentApiController(AdminContentService service) { this.service = service; }

    @GetMapping("/topics")
    public ResponseEntity<PageResponse<AdminTopicResponse>> topics(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) AdminContentVisibility visibility, Authentication authentication) {
        try {
            return ResponseEntity.ok(PageResponse.from(service.topics(page, size, visibility, authentication.getName())));
        } catch (DataAccessException | TransactionException ex) {
            throw new AdminContentOperationException(Operation.LIST, "TOPIC", null, ex);
        }
    }

    @GetMapping("/topics/{id}")
    public ResponseEntity<AdminTopicResponse> topic(@PathVariable Long id, Authentication authentication) {
        try {
            return ResponseEntity.ok(service.topic(id, authentication.getName()));
        } catch (DataAccessException | TransactionException ex) {
            throw new AdminContentOperationException(Operation.DETAIL, "TOPIC", id, ex);
        }
    }

    @GetMapping("/topics/{id}/image")
    public ResponseEntity<byte[]> topicImage(@PathVariable Long id, Authentication authentication) {
        try {
            var image = service.topicImage(id, authentication.getName());
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .contentType(MediaType.parseMediaType(image.getContentType()))
                    .contentLength(image.getByteSize())
                    .header("X-Content-Type-Options", "nosniff")
                    .header("Content-Security-Policy", "default-src 'none'; sandbox")
                    .body(image.getContent());
        } catch (DataAccessException | TransactionException ex) {
            throw new AdminContentOperationException(Operation.IMAGE, "TOPIC", id, ex);
        }
    }

    @DeleteMapping("/topics/{id}")
    public ResponseEntity<Void> deleteTopic(@PathVariable Long id, Authentication authentication) {
        try {
            service.deleteTopic(id, authentication.getName());
            return ResponseEntity.noContent().build();
        } catch (DataAccessException | TransactionException ex) {
            throw new AdminContentOperationException(Operation.DELETE, "TOPIC", id, ex);
        }
    }

    @GetMapping("/answers")
    public ResponseEntity<PageResponse<AdminAnswerResponse>> answers(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) AdminContentVisibility visibility, Authentication authentication) {
        try {
            return ResponseEntity.ok(PageResponse.from(service.answers(page, size, visibility, authentication.getName())));
        } catch (DataAccessException | TransactionException ex) {
            throw new AdminContentOperationException(Operation.LIST, "ANSWER", null, ex);
        }
    }

    @GetMapping("/answers/{id}")
    public ResponseEntity<AdminAnswerResponse> answer(@PathVariable Long id, Authentication authentication) {
        try {
            return ResponseEntity.ok(service.answer(id, authentication.getName()));
        } catch (DataAccessException | TransactionException ex) {
            throw new AdminContentOperationException(Operation.DETAIL, "ANSWER", id, ex);
        }
    }

    @DeleteMapping("/answers/{id}")
    public ResponseEntity<Void> deleteAnswer(@PathVariable Long id, Authentication authentication) {
        try {
            service.deleteAnswer(id, authentication.getName());
            return ResponseEntity.noContent().build();
        } catch (DataAccessException | TransactionException ex) {
            throw new AdminContentOperationException(Operation.DELETE, "ANSWER", id, ex);
        }
    }
}
