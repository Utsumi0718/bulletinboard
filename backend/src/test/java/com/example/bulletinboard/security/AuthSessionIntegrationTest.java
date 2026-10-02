package com.example.bulletinboard.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.example.bulletinboard.model.Profile;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.ProfileRepository;
import com.example.bulletinboard.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 実際のSecurityフィルタを通し、Cookie／Session認証とCSRFの契約を確認する。 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthSessionIntegrationTest {
    private static final String LOGIN = "/api/auth/login";
    private static final String LOGOUT = "/api/auth/logout";
    private static final String MEMBER = "session-member@example.com";
    private static final String ADMIN = "session-admin@example.com";
    private static final String PASSWORD = "Password123";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired ProfileRepository profiles;
    @Autowired PasswordEncoder encoder;

    private User member;
    private Profile memberProfile;

    @BeforeEach
    void setUp() {
        member = saveAccount("session-member", MEMBER, "ROLE_USER");
        memberProfile = saveProfile(member);
        User admin = saveAccount("session-admin", ADMIN, "ROLE_ADMIN");
        saveProfile(admin);
    }

    @Test
    void anonymousMeReturnsOnlyAuthenticationState() throws Exception {
        String body = mvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.authenticated").value(false))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("userId", "profileId", "username", "role", "email", "password");
    }

    @Test
    void authenticatedMeReturnsOnlyPublicIdentity() throws Exception {
        MockHttpSession session = login(MEMBER);

        String body = mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.userId").value(member.getId()))
                .andExpect(jsonPath("$.profileId").value(memberProfile.getId()))
                .andExpect(jsonPath("$.username").value("session-member"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(MEMBER, PASSWORD, "failedAttempt", "accountNonLocked");
    }

    @Test
    void authenticatedMeReturnsAdminRole() throws Exception {
        MockHttpSession session = login(ADMIN);

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.role").value("ROLE_ADMIN"));
    }

    @Test
    void loginSuccessResponseContainsNoCredentialsOrRedirect() throws Exception {
        Csrf csrf = fetchCsrf(null);
        String body = performLogin(csrf, MEMBER, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().doesNotExist("Location"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(MEMBER, PASSWORD, "password");
    }

    @Test
    void loginChangesSessionIdAndStoresSecurityContext() throws Exception {
        Csrf csrf = fetchCsrf(null);
        String originalId = csrf.session().getId();

        MvcResult result = performLogin(csrf, MEMBER, PASSWORD)
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession authenticated = (MockHttpSession) result.getRequest().getSession(false);

        assertThat(authenticated).isNotNull();
        assertThat(authenticated.getId()).isNotEqualTo(originalId);
        assertThat(authenticated.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNotNull();
    }

    @Test
    void realLoginAllowsProtectedApiAndLogoutEndsSession() throws Exception {
        MockHttpSession session = login(MEMBER);
        mvc.perform(get("/api/topics").session(session)).andExpect(status().isOk());

        Csrf csrf = fetchCsrf(session);
        mvc.perform(post(LOGOUT).session(session).header(csrf.headerName(), csrf.token()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andExpect(header().doesNotExist("Location"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(cookie().maxAge("JSESSIONID", 0));
        assertThat(session.isInvalid()).isTrue();

        mvc.perform(get("/api/topics"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/topics"));
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void authenticationRotatesCsrfTokenAndRejectsOldToken() throws Exception {
        Csrf before = fetchCsrf(null);
        performLogin(before, MEMBER, PASSWORD).andExpect(status().isOk());
        Csrf after = fetchCsrf(before.session());

        assertThat(after.token()).isNotEqualTo(before.token());
        mvc.perform(post(LOGOUT).session(after.session()).header(before.headerName(), before.token()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").session(after.session()))
                .andExpect(jsonPath("$.authenticated").value(true));
        mvc.perform(post(LOGOUT).session(after.session()).header(after.headerName(), after.token()))
                .andExpect(status().isNoContent());
    }

    @Test
    void loginWithoutCsrfIsForbiddenAndDoesNotCountAsCredentialFailure() throws Exception {
        mvc.perform(post(LOGIN).param("email", MEMBER).param("password", PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.path").value(LOGIN));
        assertThat(users.findByEmail(MEMBER).orElseThrow().getFailedAttempt()).isZero();
    }

    @Test
    void logoutWithoutCsrfIsForbiddenAndKeepsAuthentication() throws Exception {
        MockHttpSession session = login(MEMBER);

        mvc.perform(post(LOGOUT).session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "'','Password123',EMAIL_REQUIRED",
            "session-member@example.com,'',PASSWORD_REQUIRED",
            "'','',CREDENTIALS_REQUIRED"
    })
    void missingCredentialsReturnSpecificReason(String email, String password, String reason) throws Exception {
        mvc.perform(post(LOGIN).param("email", email).param("password", password)
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reason").value(reason))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value(LOGIN));
    }

    private MockHttpSession login(String email) throws Exception {
        Csrf csrf = fetchCsrf(null);
        MvcResult result = performLogin(csrf, email, PASSWORD)
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private org.springframework.test.web.servlet.ResultActions performLogin(
            Csrf csrf, String email, String password) throws Exception {
        return mvc.perform(post(LOGIN).session(csrf.session())
                .header(csrf.headerName(), csrf.token())
                .param("email", email).param("password", password));
    }

    private Csrf fetchCsrf(MockHttpSession session) throws Exception {
        var request = get("/api/csrf");
        if (session != null) request.session(session);
        MvcResult result = mvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();
        JsonNode json = mapper.readTree(result.getResponse().getContentAsString());
        return new Csrf(
                (MockHttpSession) result.getRequest().getSession(false),
                json.get("headerName").asText(),
                json.get("token").asText());
    }

    private User saveAccount(String username, String email, String role) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(encoder.encode(PASSWORD));
        user.setRole(role);
        return users.saveAndFlush(user);
    }

    private Profile saveProfile(User user) {
        Profile profile = new Profile();
        profile.setUser(user);
        return profiles.saveAndFlush(profile);
    }

    private record Csrf(MockHttpSession session, String headerName, String token) { }
}
