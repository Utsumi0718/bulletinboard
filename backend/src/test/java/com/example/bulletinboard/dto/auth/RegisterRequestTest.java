package com.example.bulletinboard.dto.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.example.bulletinboard.dto.user.RegisterForm;

class RegisterRequestTest {
    @Test
    void registrationDtosDoNotExposeCredentialsInStringRepresentation() {
        RegisterRequest request = new RegisterRequest(
                "secretUser1", "secret@example.com", "SecretPassword1");
        assertThat(request.toString())
                .doesNotContain(request.username(), request.email(), request.password())
                .contains("REDACTED");

        RegisterForm legacyForm = new RegisterForm();
        legacyForm.setUsername(request.username());
        legacyForm.setEmail(request.email());
        legacyForm.setPassword(request.password());
        assertThat(legacyForm.toString())
                .doesNotContain(request.username(), request.email(), request.password())
                .contains("REDACTED");
    }
}
