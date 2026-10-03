package com.example.bulletinboard.controller.api;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.bulletinboard.dto.auth.AuthMessageResponse;
import com.example.bulletinboard.dto.auth.PasswordResetConfirmRequest;
import com.example.bulletinboard.dto.auth.PasswordResetRequest;
import com.example.bulletinboard.service.PasswordResetSubmissionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth/password-reset")
public class PasswordResetApiController {
    private static final String ACCEPTED_MESSAGE =
            "登録済みのメールアドレスの場合、パスワード再設定メールを送信します。";
    private final PasswordResetSubmissionService reset;

    public PasswordResetApiController(PasswordResetSubmissionService reset) {
        this.reset = reset;
    }

    @PostMapping("/request")
    public ResponseEntity<AuthMessageResponse> request(@Valid @RequestBody PasswordResetRequest request) {
        reset.request(request.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .cacheControl(CacheControl.noStore())
                .body(new AuthMessageResponse(ACCEPTED_MESSAGE));
    }

    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(@Valid @RequestBody PasswordResetConfirmRequest request) {
        reset.confirm(request);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
