package com.example.bulletinboard.dto.security;

/**
 * 【クラスの役割】
 * CSRFトークンと、送信時に使用するHTTPヘッダー名を返すDTOです。
 *
 * @param headerName トークンを送信するヘッダー名
 * @param token CSRFトークン
 */
public record CsrfTokenResponse(
        String headerName,
        String token
) {
}