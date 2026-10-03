package com.example.bulletinboard.dto.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RegisterRequestTest {
    @Test
    void registrationDtosDoNotExposeCredentialsInStringRepresentation() {
        RegisterRequest request = new RegisterRequest(
                "secretUser1", "secret@example.com", "SecretPassword1");
        assertThat(request.toString())
                .doesNotContain(request.username(), request.email(), request.password())
                .contains("REDACTED");

    }
}
