package com.example.bulletinboard.dto.auth;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 現在の認証状態と、認証済み本人へ公開できる最小限の情報。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthenticatedUserResponse(
        boolean authenticated,
        Long userId,
        Long profileId,
        String username,
        String role) {

    public static AuthenticatedUserResponse anonymous() {
        return new AuthenticatedUserResponse(false, null, null, null, null);
    }
}
