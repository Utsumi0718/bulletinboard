package com.example.bulletinboard.controller.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.bulletinboard.exception.handler.GlobalExceptionHandler;
import com.example.bulletinboard.model.AccountStatus;
import com.example.bulletinboard.model.Answer;
import com.example.bulletinboard.model.Topic;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.AdminOperationLogRepository;
import com.example.bulletinboard.repository.AnswerRepository;
import com.example.bulletinboard.repository.TopicRepository;
import com.example.bulletinboard.repository.UserRepository;
import com.example.bulletinboard.security.SecurityConfig;
import com.example.bulletinboard.service.AdminUserService;
import com.example.bulletinboard.service.CustomUserDetailsService;

/** E-2のAPI・DB・実Security設定を専用H2で確認する。 */
@SpringBootTest(classes = AdminUserFlowIntegrationTest.FlowConfiguration.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-user-flow;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=false", "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminUserFlowIntegrationTest {
    private static final String ADMIN = "admin@example.com";
    private static final String OTHER_ADMIN = "other-admin@example.com";
    private static final String MEMBER = "member@example.com";

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = User.class)
    @EnableJpaRepositories(basePackageClasses = UserRepository.class)
    @Import({AdminUserApiController.class, AdminUserService.class,
            CustomUserDetailsService.class, SecurityConfig.class, GlobalExceptionHandler.class})
    static class FlowConfiguration { }

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TopicRepository topics;
    @Autowired AnswerRepository answers;
    @Autowired AdminOperationLogRepository logs;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        logs.deleteAll();
        answers.deleteAll();
        topics.deleteAll();
        users.deleteAll();
        users.saveAndFlush(account("admin", ADMIN, "ROLE_ADMIN"));
        users.saveAndFlush(account("other-admin", OTHER_ADMIN, "ROLE_ADMIN"));
        users.saveAndFlush(account("member", MEMBER, "ROLE_USER"));
    }

    @Test
    void listIsPagedAndNeverReturnsPassword() throws Exception {
        String body = mvc.perform(get("/api/admin/users").with(user(ADMIN).roles("ADMIN"))
                .param("size", "2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("test-password", "password");
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,-1", "0,101"})
    void invalidPageConditionsReturn400(int page, int size) throws Exception {
        mvc.perform(get("/api/admin/users").with(user(ADMIN).roles("ADMIN"))
                .param("page", String.valueOf(page)).param("size", String.valueOf(size)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void detailIncludesAccountAndSecurityLockSeparately() throws Exception {
        User member = member();
        member.setFailedAttempt(3);
        member.setAccountNonLocked(false);
        users.saveAndFlush(member);
        mvc.perform(get("/api/admin/users/{id}", member.getId()).with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.accountNonLocked").value(false))
                .andExpect(jsonPath("$.failedAttempt").value(3));
    }

    @Test
    void missingUserReturns404() throws Exception {
        mvc.perform(get("/api/admin/users/999999").with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void topicsAndAnswersIncludeDeletedAndArePaged() throws Exception {
        Topic topic = topic();
        topic.setDeletedAt(LocalDateTime.now());
        topics.saveAndFlush(topic);
        Answer answer = answer(topic);
        answer.setDeletedAt(LocalDateTime.now());
        answers.saveAndFlush(answer);
        mvc.perform(get("/api/admin/users/{id}/topics", member().getId())
                .with(user(ADMIN).roles("ADMIN")).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].content").value("topic-title"))
                .andExpect(jsonPath("$.content[0].deletedAt").exists());
        mvc.perform(get("/api/admin/users/{id}/answers", member().getId())
                .with(user(ADMIN).roles("ADMIN")).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].content").value("answer-content"))
                .andExpect(jsonPath("$.content[0].topicId").value(topic.getId()))
                .andExpect(jsonPath("$.content[0].deletedAt").exists());
    }

    @Test
    void activitiesForMissingUserReturn404() throws Exception {
        mvc.perform(get("/api/admin/users/999999/topics").with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/users/999999/answers").with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @CsvSource({"topics,-1,20", "topics,0,101", "answers,-1,20", "answers,0,0"})
    void invalidActivityPageReturns400(String kind, int page, int size) throws Exception {
        mvc.perform(get("/api/admin/users/{id}/{kind}", member().getId(), kind)
                .with(user(ADMIN).roles("ADMIN"))
                .param("page", String.valueOf(page)).param("size", String.valueOf(size)))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @CsvSource({"ACTIVE,FROZEN", "FROZEN,ACTIVE"})
    void statusChangeWritesOneSuccessfulHistoryWithoutTouchingLoginLock(String before, String next) throws Exception {
        User member = member();
        member.setAccountStatus(AccountStatus.valueOf(before));
        member.setFailedAttempt(2);
        member.setAccountNonLocked(false);
        users.saveAndFlush(member);
        mvc.perform(patch("/api/admin/users/{id}/status", member.getId())
                .with(user(ADMIN).roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + next + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountStatus").value(next))
                .andExpect(jsonPath("$.accountNonLocked").value(false))
                .andExpect(jsonPath("$.failedAttempt").value(2));
        User stored = member();
        assertThat(stored.getAccountStatus()).isEqualTo(AccountStatus.valueOf(next));
        assertThat(stored.getFailedAttempt()).isEqualTo(2);
        assertThat(stored.isAccountNonLocked()).isFalse();
        var history = logs.findByTargetTypeAndTargetIdOrderByCreatedAtAscIdAsc("USER", member.getId());
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().getAdminUser().getId())
                .isEqualTo(users.findByEmail(ADMIN).orElseThrow().getId());
        assertThat(history.getFirst().getBeforeStatus()).isEqualTo(before);
        assertThat(history.getFirst().getAfterStatus()).isEqualTo(next);
        assertThat(history.getFirst().getResult()).isEqualTo("SUCCESS");
    }

    @Test
    void repeatedRequestKeepsStatusAndSingleHistory() throws Exception {
        Long id = member().getId();
        for (int i = 0; i < 2; i++) {
            mvc.perform(patch("/api/admin/users/{id}/status", id).with(user(ADMIN).roles("ADMIN"))
                    .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\":\"FROZEN\"}"))
                    .andExpect(status().isOk());
        }
        assertThat(member().getAccountStatus()).isEqualTo(AccountStatus.FROZEN);
        assertThat(logs.findByTargetTypeAndTargetIdOrderByCreatedAtAscIdAsc("USER", id)).hasSize(1);
    }

    @Test
    void nullStatusIsRejectedWithoutHistory() throws Exception {
        mvc.perform(patch("/api/admin/users/{id}/status", member().getId())
                .with(user(ADMIN).roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        assertThat(logs.count()).isZero();
    }

    @Test
    void missingTargetCannotBeChanged() throws Exception {
        mvc.perform(patch("/api/admin/users/999999/status")
                .with(user(ADMIN).roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"FROZEN\"}"))
                .andExpect(status().isNotFound());
        assertThat(logs.count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"SELF", "OTHER_ADMIN", "WITHDRAWN"})
    void protectedTargetsCannotBeChanged(String target) throws Exception {
        User user = switch (target) {
            case "SELF" -> users.findByEmail(ADMIN).orElseThrow();
            case "OTHER_ADMIN" -> users.findByEmail(OTHER_ADMIN).orElseThrow();
            default -> member();
        };
        if (target.equals("WITHDRAWN")) {
            user.setAccountStatus(AccountStatus.WITHDRAWN);
            users.saveAndFlush(user);
        }
        mvc.perform(patch("/api/admin/users/{id}/status", user.getId())
                .with(user(ADMIN).roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"FROZEN\"}"))
                .andExpect(status().isForbidden());
        assertThat(logs.count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"WITHDRAWN", "INVALID"})
    void unsupportedStatusReturns400(String next) throws Exception {
        mvc.perform(patch("/api/admin/users/{id}/status", member().getId())
                .with(user(ADMIN).roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + next + "\"}"))
                .andExpect(status().isBadRequest());
        assertThat(logs.count()).isZero();
    }

    @Test
    void numericStatusIsRejected() throws Exception {
        mvc.perform(patch("/api/admin/users/{id}/status", member().getId())
                .with(user(ADMIN).roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":0}"))
                .andExpect(status().isBadRequest());
        assertThat(logs.count()).isZero();
    }

    @Test
    void missingActorCannotProceed() throws Exception {
        mvc.perform(patch("/api/admin/users/{id}/status", member().getId())
                .with(user("missing@example.com").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"FROZEN\"}"))
                .andExpect(status().isInternalServerError());
        assertThat(member().getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(logs.count()).isZero();
    }

    @Test
    void ordinaryUserCannotReadOrChangeAdminData() throws Exception {
        mvc.perform(get("/api/admin/users").with(user(MEMBER).roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/users/{id}/status", member().getId())
                .with(user(MEMBER).roles("USER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"FROZEN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ordinaryUserCannotUseLegacyAdminPost() throws Exception {
        mvc.perform(post("/admin/users/{id}/toggle-lock", member().getId())
                .with(user(MEMBER).roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(member().getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void anonymousAndMissingCsrfAreRejected() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/admin/users/{id}/status", member().getId())
                .with(user(ADMIN).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"FROZEN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void historyWriteFailureRollsBackFreeze() throws Exception {
        Long id = member().getId();
        jdbc.execute("ALTER TABLE admin_operation_logs ADD CONSTRAINT ck_user_history_fail "
                + "CHECK (target_type <> 'USER')");
        try {
            mvc.perform(patch("/api/admin/users/{id}/status", id)
                    .with(user(ADMIN).roles("ADMIN")).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"FROZEN\"}"))
                    .andExpect(status().isInternalServerError());
            assertThat(member().getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(logs.count()).isZero();
        } finally {
            jdbc.execute("ALTER TABLE admin_operation_logs DROP CONSTRAINT ck_user_history_fail");
        }
    }

    @ParameterizedTest
    @CsvSource({"member@example.com,ROLE_USER", "admin@example.com,ROLE_ADMIN"})
    void alreadyLoggedInSessionIsRejectedAfterFreeze(String email, String role) throws Exception {
        MvcResult login = mvc.perform(post("/login").with(csrf())
                .param("email", email).param("password", "test-password"))
                .andExpect(status().is3xxRedirection()).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();
        User account = users.findByEmail(email).orElseThrow();
        account.setAccountStatus(AccountStatus.FROZEN);
        users.saveAndFlush(account);
        mvc.perform(get(role.equals("ROLE_ADMIN") ? "/api/admin/users" : "/posts")
                .session(session))
                .andExpect(status().isForbidden());
    }

    private User account(String name, String email, String role) {
        User user = new User();
        user.setUsername(name);
        user.setEmail(email);
        user.setRole(role);
        user.setPassword(encoder.encode("test-password"));
        return user;
    }

    private User member() { return users.findByEmail(MEMBER).orElseThrow(); }

    private Topic topic() {
        Topic topic = new Topic();
        topic.setUser(member());
        topic.setTitle("topic-title");
        topic.setQuestion("topic-question");
        topic.setImage("/api/topic-images/11111111-1111-1111-1111-111111111111");
        return topics.saveAndFlush(topic);
    }

    private Answer answer(Topic topic) {
        Answer answer = new Answer();
        answer.setUser(member());
        answer.setTopic(topic);
        answer.setContent("answer-content");
        return answers.saveAndFlush(answer);
    }
}
