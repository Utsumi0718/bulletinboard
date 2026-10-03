package com.example.bulletinboard.dto.auth;

/** ログイン失敗理由をReact側で判定できる安全な応答。 */
public record LoginFailureResponse(
        int status,
        String error,
        String message,
        String path,
        String reason) {
}
