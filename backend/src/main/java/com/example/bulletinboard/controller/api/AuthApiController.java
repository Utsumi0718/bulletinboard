package com.example.bulletinboard.controller.api;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.bulletinboard.dto.auth.RegisterRequest;
import com.example.bulletinboard.dto.auth.RegisterResponse;
import com.example.bulletinboard.service.RegistrationSubmissionService;

import jakarta.validation.Valid;

/** Cookie／Session認証で使用する認証関連API。F-2では登録だけを提供する。 */
@RestController
@RequestMapping("/api/auth")
public class AuthApiController {
    private final RegistrationSubmissionService registration;

    public AuthApiController(RegistrationSubmissionService registration) {
        this.registration = registration;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(registration.register(request));
    }
}
