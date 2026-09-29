package com.example.bulletinboard.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.bulletinboard.dto.common.PageResponse;
import com.example.bulletinboard.dto.user.AdminUserActivityResponse;
import com.example.bulletinboard.dto.user.AdminUserResponse;
import com.example.bulletinboard.dto.user.AdminUserStatusRequest;
import com.example.bulletinboard.exception.AdminUserOperationException;
import com.example.bulletinboard.exception.AdminUserOperationException.Operation;
import com.example.bulletinboard.service.AdminUserService;

import jakarta.validation.Valid;

/** 管理者専用User API。認可はSecurityとServiceで確認する。 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserApiController {
    private final AdminUserService service;

    public AdminUserApiController(AdminUserService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<PageResponse<AdminUserResponse>> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, Authentication authentication) {
        try {
            return ResponseEntity.ok(PageResponse.from(service.list(page, size, authentication.getName())));
        } catch (DataAccessException | TransactionException ex) {
            throw new AdminUserOperationException(Operation.LIST, null, ex);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminUserResponse> detail(@PathVariable Long id, Authentication authentication) {
        try {
            return ResponseEntity.ok(service.detail(id, authentication.getName()));
        } catch (DataAccessException | TransactionException ex) {
            throw new AdminUserOperationException(Operation.DETAIL, id, ex);
        }
    }

    @GetMapping("/{id}/topics")
    public ResponseEntity<PageResponse<AdminUserActivityResponse>> topics(@PathVariable Long id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        try {
            return ResponseEntity.ok(PageResponse.from(service.topics(id, page, size, authentication.getName())));
        } catch (DataAccessException | TransactionException ex) {
            throw new AdminUserOperationException(Operation.TOPICS, id, ex);
        }
    }

    @GetMapping("/{id}/answers")
    public ResponseEntity<PageResponse<AdminUserActivityResponse>> answers(@PathVariable Long id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        try {
            return ResponseEntity.ok(PageResponse.from(service.answers(id, page, size, authentication.getName())));
        } catch (DataAccessException | TransactionException ex) {
            throw new AdminUserOperationException(Operation.ANSWERS, id, ex);
        }
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<AdminUserResponse> status(@PathVariable Long id,
            @Valid @RequestBody AdminUserStatusRequest request, Authentication authentication) {
        try {
            return ResponseEntity.ok(service.setStatus(id, request.status(), authentication.getName()));
        } catch (DataAccessException | TransactionException ex) {
            throw new AdminUserOperationException(Operation.STATUS_CHANGE, id, ex);
        }
    }
}
