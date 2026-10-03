package com.example.bulletinboard.controller.api;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.bulletinboard.dto.auth.RegisterRequest;
import com.example.bulletinboard.dto.auth.RegisterResponse;
import com.example.bulletinboard.dto.auth.AuthenticatedUserResponse;
import com.example.bulletinboard.service.AuthenticatedUserService;
import com.example.bulletinboard.service.RegistrationSubmissionService;

import jakarta.validation.Valid;

/** Cookie／Session認証で使用する登録・ログイン状態確認API。 */
@RestController
@RequestMapping("/api/auth")
public class AuthApiController {
    private final RegistrationSubmissionService registration;
    private final AuthenticatedUserService authenticatedUsers;

    public AuthApiController(RegistrationSubmissionService registration,
            AuthenticatedUserService authenticatedUsers) {
        this.registration = registration;
        this.authenticatedUsers = authenticatedUsers;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(registration.register(request));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthenticatedUserResponse> me(Authentication authentication) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(authenticatedUsers.current(authentication));
    }
}
