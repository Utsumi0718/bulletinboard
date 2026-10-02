package com.example.bulletinboard.controller.api;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.bulletinboard.dto.auth.WithdrawalRequest;
import com.example.bulletinboard.service.WithdrawalSubmissionService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/account")
public class AccountApiController {
    private final WithdrawalSubmissionService withdrawal;

    public AccountApiController(WithdrawalSubmissionService withdrawal) {
        this.withdrawal = withdrawal;
    }

    @PostMapping("/withdraw")
    public ResponseEntity<Void> withdraw(@Valid @RequestBody WithdrawalRequest request,
            Authentication authentication, HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        withdrawal.withdraw(authentication.getName(), request.password());
        new SecurityContextLogoutHandler().logout(
                servletRequest, servletResponse, authentication);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
