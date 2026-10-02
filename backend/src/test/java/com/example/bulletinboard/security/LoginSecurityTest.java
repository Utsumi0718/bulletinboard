package com.example.bulletinboard.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.bulletinboard.model.AccountStatus;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.UserRepository;

/** ログイン結果と、失敗回数・ロック・利用状態の既存動作をAPI契約で確認する。 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LoginSecurityTest {
    private static final String LOGIN = "/api/auth/login";
    private static final String EMAIL = "testuser@example.com";
    private static final String PASSWORD = "Password123";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setUsername("testuser");
        user.setEmail(EMAIL);
        user.setPassword(encoder.encode(PASSWORD));
        user.setFailedAttempt(0);
        user.setAccountNonLocked(true);
        user.setAccountStatus(AccountStatus.ACTIVE);
        users.save(user);
    }

    @Test
    void wrongPasswordReturnsJsonAndIncreasesFailureCount() throws Exception {
        login("WrongPass1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reason").value("WRONG_CREDENTIALS"))
                .andExpect(jsonPath("$.path").value(LOGIN))
                .andExpect(header().string("Cache-Control", "no-store"));

        User user = currentUser();
        assertEquals(1, user.getFailedAttempt());
        assertTrue(user.isAccountNonLocked());
    }

    @Test
    void threeFailuresLockAccountAndNextAttemptReportsLocked() throws Exception {
        login("Wrong1").andExpect(status().isUnauthorized());
        login("Wrong2").andExpect(status().isUnauthorized());
        login("Wrong3")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reason").value("WRONG_CREDENTIALS"));

        User user = currentUser();
        assertEquals(3, user.getFailedAttempt());
        assertFalse(user.isAccountNonLocked());

        login("Wrong4")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reason").value("LOCKED"));
    }

    @Test
    void preLockedAccountReturnsLocked() throws Exception {
        User user = currentUser();
        user.setFailedAttempt(3);
        user.setAccountNonLocked(false);
        users.save(user);

        login(PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reason").value("LOCKED"));
    }

    @Test
    void activeAccountReturnsJsonSuccess() throws Exception {
        login(PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(header().doesNotExist("Location"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void frozenAccountReturnsFrozen() throws Exception {
        User user = currentUser();
        user.setAccountStatus(AccountStatus.FROZEN);
        users.save(user);

        login(PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reason").value("FROZEN"));
    }

    @Test
    void withdrawnAccountReturnsWithdrawn() throws Exception {
        User user = currentUser();
        user.setAccountStatus(AccountStatus.WITHDRAWN);
        users.save(user);

        login(PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reason").value("WITHDRAWN"));
    }

    @Test
    void successfulLoginResetsFailureCount() throws Exception {
        User user = currentUser();
        user.setFailedAttempt(2);
        users.save(user);

        login(PASSWORD).andExpect(status().isOk());

        User updated = currentUser();
        assertEquals(0, updated.getFailedAttempt());
        assertTrue(updated.isAccountNonLocked());
    }

    private org.springframework.test.web.servlet.ResultActions login(String password) throws Exception {
        return mvc.perform(post(LOGIN).with(csrf()).param("email", EMAIL).param("password", password));
    }

    private User currentUser() {
        return users.findByEmail(EMAIL).orElseThrow();
    }
}
