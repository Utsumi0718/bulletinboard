package com.example.bulletinboard.controller.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import com.example.bulletinboard.exception.handler.GlobalExceptionHandler;
import com.example.bulletinboard.model.Profile;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.ProfileRepository;
import com.example.bulletinboard.repository.UserRepository;
import com.example.bulletinboard.security.SecurityConfig;
import com.example.bulletinboard.service.CustomUserDetailsService;
import com.example.bulletinboard.service.AuthenticatedUserService;
import com.example.bulletinboard.service.RegistrationService;
import com.example.bulletinboard.service.RegistrationSubmissionService;
import com.fasterxml.jackson.databind.ObjectMapper;

/** F-2の登録API、User・Profile保存、実Security設定を専用H2で確認する。 */
@SpringBootTest(classes = RegistrationApiIntegrationTest.TestConfiguration.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:registration-api;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=false", "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RegistrationApiIntegrationTest {
    private static final String PATH = "/api/auth/register";
    private static final String RAW_PASSWORD = "Password123";

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = User.class)
    @EnableJpaRepositories(basePackageClasses = UserRepository.class)
    @Import({AuthApiController.class, AuthenticatedUserService.class,
            RegistrationService.class, RegistrationSubmissionService.class,
            CustomUserDetailsService.class, SecurityConfig.class, GlobalExceptionHandler.class})
    static class TestConfiguration { }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired ProfileRepository profiles;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        profiles.deleteAll();
        users.deleteAll();
    }

    @Test
    void validRegistrationCreatesUserAndEmptyProfileWithoutExposingCredentials() throws Exception {
        String body = mvc.perform(post(PATH).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("member01", "member@example.com", RAW_PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.username").value("member01"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("member@example.com", RAW_PASSWORD, "password");
        User saved = users.findByEmail("member@example.com").orElseThrow();
        assertThat(saved.getRole()).isEqualTo("ROLE_USER");
        assertThat(saved.getFailedAttempt()).isZero();
        assertThat(saved.isAccountNonLocked()).isTrue();
        assertThat(saved.getPassword()).isNotEqualTo(RAW_PASSWORD);
        assertThat(passwords.matches(RAW_PASSWORD, saved.getPassword())).isTrue();
        Profile profile = profiles.findByUserId(saved.getId()).orElseThrow();
        assertThat(profile.getIcon()).isNull();
        assertThat(profile.getBio()).isNull();
        assertThat(profiles.count()).isOne();
    }

    @Test
    void missingCsrfIsRejectedWithoutSaving() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                .content(json("member01", "member@example.com", RAW_PASSWORD)))
                .andExpect(status().isForbidden());
        assertThat(users.count()).isZero();
        assertThat(profiles.count()).isZero();
    }

    @Test
    void malformedJsonReturnsSafe400() throws Exception {
        mvc.perform(post(PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"member01\",\"password\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value(PATH));
        assertThat(users.count()).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidInputs")
    void invalidInputReturns400AndDoesNotSave(String name, String field,
            String username, String email, String password) throws Exception {
        mvc.perform(post(PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json(username, email, password)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors." + field).exists());
        assertThat(users.count()).isZero();
        assertThat(profiles.count()).isZero();
    }

    static Stream<Arguments> invalidInputs() {
        return Stream.of(
                Arguments.of("username blank", "username", " ", "member@example.com", RAW_PASSWORD),
                Arguments.of("username too short", "username", "a1", "member@example.com", RAW_PASSWORD),
                Arguments.of("username too long", "username", "abcdefghij1", "member@example.com", RAW_PASSWORD),
                Arguments.of("username format", "username", "abcdefgh", "member@example.com", RAW_PASSWORD),
                Arguments.of("email blank", "email", "member01", " ", RAW_PASSWORD),
                Arguments.of("email format", "email", "member01", "invalid", RAW_PASSWORD),
                Arguments.of("email too long", "email", "member01", "a".repeat(244) + "@example.com", RAW_PASSWORD),
                Arguments.of("password blank", "password", "member01", "member@example.com", " "),
                Arguments.of("password too short", "password", "member01", "member@example.com", "a1"),
                Arguments.of("password too long", "password", "member01", "member@example.com", "a".repeat(20) + "1"),
                Arguments.of("password format", "password", "member01", "member@example.com", "abcdefgh"));
    }

    @Test
    void duplicateUsernameReturns409WithFieldError() throws Exception {
        register("member01", "first@example.com");
        mvc.perform(post(PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json("member01", "second@example.com", RAW_PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.username").exists());
        assertThat(users.count()).isOne();
        assertThat(profiles.count()).isOne();
    }

    @Test
    void duplicateEmailReturns409WithFieldError() throws Exception {
        register("member01", "member@example.com");
        mvc.perform(post(PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json("member02", "member@example.com", RAW_PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
        assertThat(users.count()).isOne();
        assertThat(profiles.count()).isOne();
    }

    @Test
    void profileSaveFailureRollsBackUserAndReturnsSafe500() throws Exception {
        jdbc.execute("ALTER TABLE profiles ADD CONSTRAINT ck_registration_profile_fail CHECK (bio IS NOT NULL)");
        try {
            String body = mvc.perform(post(PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(json("member01", "secret@example.com", RAW_PASSWORD)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.message").value(
                            "アカウントを登録できませんでした。時間をおいて再度お試しください。"))
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain("secret@example.com", RAW_PASSWORD, "CHECK", "SQL");
            assertThat(users.count()).isZero();
            assertThat(profiles.count()).isZero();
        } finally {
            jdbc.execute("ALTER TABLE profiles DROP CONSTRAINT ck_registration_profile_fail");
        }
    }

    private void register(String username, String email) throws Exception {
        mvc.perform(post(PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json(username, email, RAW_PASSWORD)))
                .andExpect(status().isCreated());
    }

    private String json(String username, String email, String password) throws Exception {
        return mapper.writeValueAsString(Map.of(
                "username", username, "email", email, "password", password));
    }
}
