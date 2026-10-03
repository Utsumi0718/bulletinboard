package com.example.bulletinboard.dto.auth;

/** 登録完了時に公開する最小限のUser情報。 */
public record RegisterResponse(Long userId, String username) {
}
