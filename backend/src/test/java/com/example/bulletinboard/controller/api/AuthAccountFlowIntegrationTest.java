package com.example.bulletinboard.controller.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.bulletinboard.model.AccountStatus;
import com.example.bulletinboard.model.Answer;
import com.example.bulletinboard.model.Like;
import com.example.bulletinboard.model.Topic;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.model.WinnerAchievement;
import com.example.bulletinboard.repository.AnswerRepository;
import com.example.bulletinboard.repository.LikeRepository;
import com.example.bulletinboard.repository.PasswordResetTokenRepository;
import com.example.bulletinboard.repository.TopicRepository;
import com.example.bulletinboard.repository.UserRepository;
import com.example.bulletinboard.repository.WinnerAchievementRepository;
import com.example.bulletinboard.service.PasswordResetService;

/** F-4の再設定メール、Session、退会、関連データ保持を専用H2と実Securityで確認する。 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth-account-flow;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=false", "spring.sql.init.mode=never",
        "app.password-reset.url=http://frontend.test/reset-password"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthAccountFlowIntegrationTest {
    private static final String RESET_REQUEST = "/api/auth/password-reset/request";
    private static final String RESET_CONFIRM = "/api/auth/password-reset/confirm";
    private static final String WITHDRAW = "/api/account/withdraw";
    private static final String OLD_PASSWORD = "Password123";
    private static final String NEW_PASSWORD = "NewPassword456";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordResetTokenRepository tokens;
    @Autowired TopicRepository topics;
    @Autowired AnswerRepository answers;
    @Autowired LikeRepository likes;
    @Autowired WinnerAchievementRepository achievements;
    @Autowired PasswordEncoder passwords;
    @MockitoBean JavaMailSender mailSender;

    private String email;
    private User user;

    @BeforeEach
    void setUp() {
        reset(mailSender);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        email = "member-" + suffix + "@example.com";
        user = account("member-" + suffix, email, "ROLE_USER", AccountStatus.ACTIVE);
    }

    @Test
    void resetRequestStoresOnlyHashAndSendsThirtyMinuteLink() throws Exception {
        LocalDateTime before = LocalDateTime.now();
        requestReset(email).andExpect(status().isAccepted())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.message").value(
                        "登録済みのメールアドレスの場合、パスワード再設定メールを送信します。"));

        String raw = sentToken(1);
        var stored = tokens.findByUserId(user.getId()).orElseThrow();
        assertThat(stored.getTokenHash()).isEqualTo(PasswordResetService.hash(raw)).doesNotContain(raw);
        assertThat(stored.getExpiresAt()).isBetween(before.plusMinutes(29), before.plusMinutes(31));
    }

    @Test
    void unknownAndWithdrawnEmailsReturnSameResponseWithoutMail() throws Exception {
        user.setAccountStatus(AccountStatus.WITHDRAWN);
        users.saveAndFlush(user);

        requestReset("unknown@example.com").andExpect(status().isAccepted());
        requestReset(email).andExpect(status().isAccepted());

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        assertThat(tokens.findByUserId(user.getId())).isEmpty();
    }

    @Test
    void reissueInvalidatesOldTokenAndNewTokenCanBeUsed() throws Exception {
        requestReset(email).andExpect(status().isAccepted());
        requestReset(email).andExpect(status().isAccepted());
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(2)).send(captor.capture());
        String oldToken = tokenFrom(captor.getAllValues().get(0));
        String newToken = tokenFrom(captor.getAllValues().get(1));

        confirm(oldToken, NEW_PASSWORD).andExpect(status().isBadRequest());
        confirm(newToken, NEW_PASSWORD).andExpect(status().isNoContent());
        assertThat(passwords.matches(NEW_PASSWORD, users.findById(user.getId()).orElseThrow().getPassword())).isTrue();
    }

    @Test
    void invalidExpiredAndReusedTokensAreRejected() throws Exception {
        confirm("invalid-token", NEW_PASSWORD).andExpect(status().isBadRequest());

        requestReset(email).andExpect(status().isAccepted());
        String expired = sentToken(1);
        var stored = tokens.findByUserId(user.getId()).orElseThrow();
        stored.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        tokens.saveAndFlush(stored);
        confirm(expired, NEW_PASSWORD).andExpect(status().isBadRequest());

        requestReset(email).andExpect(status().isAccepted());
        String valid = sentToken(2);
        confirm(valid, NEW_PASSWORD).andExpect(status().isNoContent());
        confirm(valid, "Another123").andExpect(status().isBadRequest());
    }

    @Test
    void resetUnlocksAccountPreservesStatusAndExpiresAllSessions() throws Exception {
        MockHttpSession first = login(email, OLD_PASSWORD);
        MockHttpSession second = login(email, OLD_PASSWORD);
        user = users.findById(user.getId()).orElseThrow();
        user.setFailedAttempt(3);
        user.setAccountNonLocked(false);
        users.saveAndFlush(user);

        requestReset(email).andExpect(status().isAccepted());
        String raw = sentToken(1);
        confirm(raw, NEW_PASSWORD).andExpect(status().isNoContent())
                .andExpect(content().string(""));

        User updated = users.findById(user.getId()).orElseThrow();
        assertThat(updated.getFailedAttempt()).isZero();
        assertThat(updated.isAccountNonLocked()).isTrue();
        assertThat(updated.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(tokens.findByUserId(user.getId())).isEmpty();
        mvc.perform(get("/api/topics").session(first)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/topics").session(second)).andExpect(status().isUnauthorized());
        login(email, NEW_PASSWORD);
    }

    @Test
    void frozenAccountCanResetPasswordButRemainsFrozen() throws Exception {
        user.setAccountStatus(AccountStatus.FROZEN);
        users.saveAndFlush(user);
        requestReset(email).andExpect(status().isAccepted());
        confirm(sentToken(1), NEW_PASSWORD).andExpect(status().isNoContent());

        User updated = users.findById(user.getId()).orElseThrow();
        assertThat(updated.getAccountStatus()).isEqualTo(AccountStatus.FROZEN);
        mvc.perform(post("/api/auth/login").with(csrf()).param("email", email)
                        .param("password", NEW_PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reason").value("FROZEN"));
    }

    @Test
    void mailFailureReturns503AndRevokesToken() throws Exception {
        doThrow(new MailSendException("internal test failure"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        requestReset(email).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.path").value(RESET_REQUEST));
        assertThat(tokens.findByUserId(user.getId())).isEmpty();
    }

    @Test
    void passwordResetEndpointsRequireCsrf() throws Exception {
        mvc.perform(post(RESET_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(RESET_CONFIRM).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"token\",\"newPassword\":\"Password123\"}"))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "REQUEST,{\"email\":\"invalid\"}",
            "REQUEST,{\"email\":\"\"}",
            "CONFIRM,{\"token\":\"\",\"newPassword\":\"Password123\"}",
            "CONFIRM,{\"token\":\"token\",\"newPassword\":\"short\"}"
    })
    void invalidResetInputReturns400(String operation, String json) throws Exception {
        String path = operation.equals("REQUEST") ? RESET_REQUEST : RESET_CONFIRM;
        mvc.perform(post(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void withdrawalRequiresCorrectPasswordAndExplicitConfirmation() throws Exception {
        MockHttpSession session = login(email, OLD_PASSWORD);
        withdraw(session, "WrongPassword1", true).andExpect(status().isForbidden());
        assertThat(users.findById(user.getId()).orElseThrow().getAccountStatus())
                .isEqualTo(AccountStatus.ACTIVE);
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(jsonPath("$.authenticated").value(true));

        withdraw(session, OLD_PASSWORD, false).andExpect(status().isBadRequest());
        assertThat(users.findById(user.getId()).orElseThrow().getAccountStatus())
                .isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void withdrawalEndsCurrentAndRejectsOtherSession() throws Exception {
        MockHttpSession current = login(email, OLD_PASSWORD);
        MockHttpSession other = login(email, OLD_PASSWORD);

        withdraw(current, OLD_PASSWORD, true)
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(""));
        assertThat(current.isInvalid()).isTrue();
        User updated = users.findById(user.getId()).orElseThrow();
        assertThat(updated.getAccountStatus()).isEqualTo(AccountStatus.WITHDRAWN);
        assertThat(updated.getWithdrawnAt()).isNotNull();
        mvc.perform(get("/api/topics").session(other)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").with(csrf()).param("email", email)
                        .param("password", OLD_PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reason").value("WITHDRAWN"));
    }

    @Test
    void withdrawalKeepsTopicsAnswersLikesAndAchievements() throws Exception {
        Topic topic = new Topic();
        topic.setUser(user);
        topic.setTitle("retained topic");
        topic.setQuestion("retained question");
        topic.setImage("/api/topic-images/11111111-1111-1111-1111-111111111111");
        topic = topics.saveAndFlush(topic);
        Answer answer = new Answer();
        answer.setUser(user);
        answer.setTopic(topic);
        answer.setContent("retained answer");
        answer = answers.saveAndFlush(answer);
        likes.saveAndFlush(new Like(user, answer));
        WinnerAchievement achievement = new WinnerAchievement();
        achievement.setUser(user);
        achievement.setTopic(topic);
        achievement.setAnswer(answer);
        achievement.setLikeCount(1);
        achievement.setAchievedAt(LocalDateTime.now());
        achievements.saveAndFlush(achievement);

        withdraw(login(email, OLD_PASSWORD), OLD_PASSWORD, true).andExpect(status().isNoContent());

        assertThat(topics.existsById(topic.getId())).isTrue();
        assertThat(answers.existsById(answer.getId())).isTrue();
        assertThat(likes.count()).isGreaterThanOrEqualTo(1);
        assertThat(achievements.findByUserOrderByAchievedAtDesc(user)).hasSize(1);
    }

    @Test
    void withdrawalRequiresAuthenticationUserRoleAndCsrf() throws Exception {
        mvc.perform(post(WITHDRAW).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(withdrawalJson(OLD_PASSWORD, true)))
                .andExpect(status().isUnauthorized());

        MockHttpSession memberSession = login(email, OLD_PASSWORD);
        mvc.perform(post(WITHDRAW).session(memberSession).contentType(MediaType.APPLICATION_JSON)
                        .content(withdrawalJson(OLD_PASSWORD, true)))
                .andExpect(status().isForbidden());

        String adminEmail = "admin-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        account("admin-" + UUID.randomUUID().toString().substring(0, 8), adminEmail,
                "ROLE_ADMIN", AccountStatus.ACTIVE);
        MockHttpSession adminSession = login(adminEmail, OLD_PASSWORD);
        withdraw(adminSession, OLD_PASSWORD, true).andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions requestReset(String targetEmail) throws Exception {
        return mvc.perform(post(RESET_REQUEST).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + targetEmail + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions confirm(String token, String password)
            throws Exception {
        return mvc.perform(post(RESET_CONFIRM).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + password + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions withdraw(
            MockHttpSession session, String password, boolean confirmed) throws Exception {
        return mvc.perform(post(WITHDRAW).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(withdrawalJson(password, confirmed)));
    }

    private String withdrawalJson(String password, boolean confirmed) {
        return "{\"password\":\"" + password + "\",\"confirmed\":" + confirmed + "}";
    }

    private MockHttpSession login(String loginEmail, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").with(csrf())
                        .param("email", loginEmail).param("password", password))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String sentToken(int invocations) {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(invocations)).send(captor.capture());
        return tokenFrom(captor.getAllValues().get(invocations - 1));
    }

    private String tokenFrom(SimpleMailMessage mail) {
        assertThat(mail.getTo()).containsExactly(email);
        return mail.getText().substring(mail.getText().indexOf("?token=") + 7);
    }

    private User account(String username, String accountEmail, String role, AccountStatus status) {
        User account = new User();
        account.setUsername(username);
        account.setEmail(accountEmail);
        account.setPassword(passwords.encode(OLD_PASSWORD));
        account.setRole(role);
        account.setAccountStatus(status);
        return users.saveAndFlush(account);
    }
}
