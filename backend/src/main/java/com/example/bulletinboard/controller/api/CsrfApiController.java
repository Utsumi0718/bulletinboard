package com.example.bulletinboard.controller.api;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.bulletinboard.dto.security.CsrfTokenResponse;

/**
 * 【クラスの役割】
 * CSRFトークンの取得APIを提供します。
 *
 * 未ログインでも取得でき、取得時と同じセッションで
 * POSTなどのリクエストを送信する際に使用します。
 */
@RestController
@RequestMapping("/api/csrf")
public class CsrfApiController {

    @GetMapping
    public ResponseEntity<CsrfTokenResponse> getCsrfToken(
            CsrfToken csrfToken) {

        CsrfTokenResponse response = new CsrfTokenResponse(
                csrfToken.getHeaderName(),
                csrfToken.getToken()
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }
}