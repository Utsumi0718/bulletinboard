package com.example.bulletinboard.controller.api;

import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.bulletinboard.dto.profile.AdminProfileResponse;
import com.example.bulletinboard.exception.AdminContentOperationException;
import com.example.bulletinboard.exception.AdminContentOperationException.Operation;
import com.example.bulletinboard.service.AdminProfileService;

/** Profile.idを対象にする管理API。User.idからの取得は別URLで明示する。 */
@RestController
@RequestMapping("/api/admin")
public class AdminProfileApiController {
    private final AdminProfileService service;

    public AdminProfileApiController(AdminProfileService service) { this.service = service; }

    @GetMapping("/profiles/{profileId}")
    public ResponseEntity<AdminProfileResponse> detail(@PathVariable Long profileId, Authentication authentication) {
        try {
            return ResponseEntity.ok(service.detail(profileId, authentication.getName()));
        } catch (DataAccessException | TransactionException ex) {
            throw new AdminContentOperationException(Operation.DETAIL, "PROFILE", profileId, ex);
        }
    }

    @GetMapping("/users/{userId}/profile")
    public ResponseEntity<AdminProfileResponse> byUser(@PathVariable Long userId, Authentication authentication) {
        try {
            return ResponseEntity.ok(service.byUser(userId, authentication.getName()));
        } catch (DataAccessException | TransactionException ex) {
            throw new AdminContentOperationException(Operation.DETAIL, "PROFILE", null, ex);
        }
    }

    @DeleteMapping("/profiles/{profileId}/icon")
    public ResponseEntity<Void> resetIcon(@PathVariable Long profileId, Authentication authentication) {
        try {
            service.resetIcon(profileId, authentication.getName());
            return ResponseEntity.noContent().build();
        } catch (DataAccessException | TransactionException ex) {
            throw new AdminContentOperationException(Operation.ICON_RESET, "PROFILE", profileId, ex);
        }
    }

    @DeleteMapping("/profiles/{profileId}/bio")
    public ResponseEntity<Void> clearBio(@PathVariable Long profileId, Authentication authentication) {
        try {
            service.clearBio(profileId, authentication.getName());
            return ResponseEntity.noContent().build();
        } catch (DataAccessException | TransactionException ex) {
            throw new AdminContentOperationException(Operation.BIO_CLEAR, "PROFILE", profileId, ex);
        }
    }
}
