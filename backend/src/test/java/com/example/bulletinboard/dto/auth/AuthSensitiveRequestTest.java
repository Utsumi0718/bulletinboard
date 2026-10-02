package com.example.bulletinboard.dto.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthSensitiveRequestTest {
    @Test
    void resetRequestToStringDoesNotExposeEmail() {
        var request = new PasswordResetRequest("secret@example.com");
        assertThat(request.toString()).doesNotContain(request.email());
    }

    @Test
    void resetConfirmToStringDoesNotExposeTokenOrPassword() {
        var request = new PasswordResetConfirmRequest("secret-token", "Password123");
        assertThat(request.toString()).doesNotContain(request.token(), request.newPassword());
    }

    @Test
    void withdrawalToStringDoesNotExposePassword() {
        var request = new WithdrawalRequest("Password123", true);
        assertThat(request.toString()).doesNotContain(request.password());
    }
}
