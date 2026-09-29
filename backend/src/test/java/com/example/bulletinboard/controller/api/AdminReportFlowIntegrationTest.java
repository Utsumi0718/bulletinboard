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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.bulletinboard.exception.handler.GlobalExceptionHandler;
import com.example.bulletinboard.model.AccountStatus;
import com.example.bulletinboard.model.Answer;
import com.example.bulletinboard.model.Profile;
import com.example.bulletinboard.model.Report;
import com.example.bulletinboard.model.ReportAnswerSnapshot;
import com.example.bulletinboard.model.ReportProfileSnapshot;
import com.example.bulletinboard.model.ReportReason;
import com.example.bulletinboard.model.ReportStatus;
import com.example.bulletinboard.model.ReportTargetType;
import com.example.bulletinboard.model.ReportTopicSnapshot;
import com.example.bulletinboard.model.Topic;
import com.example.bulletinboard.model.TopicImage;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.AdminOperationLogRepository;
import com.example.bulletinboard.repository.AnswerRepository;
import com.example.bulletinboard.repository.ProfileRepository;
import com.example.bulletinboard.repository.ReportAnswerSnapshotRepository;
import com.example.bulletinboard.repository.ReportProfileSnapshotRepository;
import com.example.bulletinboard.repository.ReportRepository;
import com.example.bulletinboard.repository.ReportTopicSnapshotRepository;
import com.example.bulletinboard.repository.TopicRepository;
import com.example.bulletinboard.repository.UserRepository;
import com.example.bulletinboard.security.SecurityConfig;
import com.example.bulletinboard.service.AdminReportService;
import com.example.bulletinboard.service.CustomUserDetailsService;

/** 実際の管理Controller・Service・Repository・Securityと専用H2でE-1を検証する。 */
@SpringBootTest(classes = AdminReportFlowIntegrationTest.FlowConfiguration.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-report-flow;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=false", "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminReportFlowIntegrationTest {
    private static final String ADMIN = "admin@example.com";
    private static final String REPORTER = "reporter@example.com";
    private static final String SECOND = "second@example.com";
    private static final String OWNER = "owner@example.com";

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = User.class)
    @EnableJpaRepositories(basePackageClasses = UserRepository.class)
    @Import({AdminReportApiController.class, AdminReportService.class,
            CustomUserDetailsService.class, SecurityConfig.class, GlobalExceptionHandler.class})
    static class FlowConfiguration { }

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ProfileRepository profiles;
    @Autowired TopicRepository topics;
    @Autowired AnswerRepository answers;
    @Autowired ReportRepository reports;
    @Autowired ReportTopicSnapshotRepository topicSnapshots;
    @Autowired ReportAnswerSnapshotRepository answerSnapshots;
    @Autowired ReportProfileSnapshotRepository profileSnapshots;
    @Autowired AdminOperationLogRepository logs;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        logs.deleteAll();
        topicSnapshots.deleteAll();
        answerSnapshots.deleteAll();
        profileSnapshots.deleteAll();
        reports.deleteAll();
        answers.deleteAll();
        topics.deleteAll();
        profiles.deleteAll();
        users.deleteAll();
        users.saveAndFlush(account("admin", ADMIN, "ROLE_ADMIN"));
        users.saveAndFlush(account("reporter", REPORTER, "ROLE_USER"));
        users.saveAndFlush(account("second", SECOND, "ROLE_USER"));
        users.saveAndFlush(account("owner", OWNER, "ROLE_USER"));
    }

    @Test
    void listFiltersByStatusAndReporterWithoutExposingDetailOrEmail() throws Exception {
        Report first = createReport(ReportTargetType.TOPIC, 11L, REPORTER);
        Report second = createReport(ReportTargetType.ANSWER, 12L, REPORTER);
        second.setStatus(ReportStatus.IN_PROGRESS);
        reports.saveAndFlush(second);
        createReport(ReportTargetType.PROFILE, 13L, SECOND);
        Long reporterId = users.findByEmail(REPORTER).orElseThrow().getId();
        var result = mvc.perform(get("/api/admin/reports").with(user(ADMIN).roles("ADMIN"))
                .param("status", "UNHANDLED").param("reporterUserId", reporterId.toString())
                .param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(first.getId()))
                .andReturn().getResponse().getContentAsString();
        assertThat(result).doesNotContain(REPORTER, OWNER, "test-password", "detail");
    }

    @ParameterizedTest
    @CsvSource({"-1,20,1", "0,0,1", "0,101,1", "0,-1,1", "0,20,-1"})
    void invalidPagingAndReporterFiltersReturn400(int page, int size, long reporterId) throws Exception {
        mvc.perform(get("/api/admin/reports").with(user(ADMIN).roles("ADMIN"))
                .param("page", Integer.toString(page)).param("size", Integer.toString(size))
                .param("reporterUserId", Long.toString(reporterId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sameTimestampUsesIdDescendingAndOutOfRangePageIsEmpty() throws Exception {
        Report first = createReport(ReportTargetType.TOPIC, 1L, REPORTER);
        Report second = createReport(ReportTargetType.TOPIC, 2L, REPORTER);
        LocalDateTime sameTime = LocalDateTime.of(2026, 1, 1, 12, 0);
        jdbc.update("UPDATE reports SET created_at = ? WHERE id IN (?, ?)",
                java.sql.Timestamp.valueOf(sameTime), first.getId(), second.getId());
        mvc.perform(get("/api/admin/reports").with(user(ADMIN).roles("ADMIN"))
                .param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(second.getId()))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/admin/reports").with(user(ADMIN).roles("ADMIN"))
                .param("page", "5").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void detailShowsTopicEvidenceAfterLogicalDeletion() throws Exception {
        Topic topic = createTopic();
        Report report = createReport(ReportTargetType.TOPIC, topic.getId(), REPORTER);
        new TransactionTemplate(transactions).executeWithoutResult(tx ->
                topicSnapshots.saveAndFlush(new ReportTopicSnapshot(reports.findById(report.getId()).orElseThrow(),
                        topics.findById(topic.getId()).orElseThrow(),
                        new TopicImage(users.findByEmail(OWNER).orElseThrow().getId(),
                                "image/png", new byte[] {1, 2, 3}))));
        topic.setDeletedAt(LocalDateTime.now());
        topics.saveAndFlush(topic);
        mvc.perform(get("/api/admin/reports/{id}", report.getId()).with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTargetState").value("DELETED"))
                .andExpect(jsonPath("$.evidence.topicTitle").value("通報時タイトル"))
                .andExpect(jsonPath("$.evidence.topicQuestion").value("通報時本文"))
                .andExpect(jsonPath("$.evidence.imageAvailable").value(true))
                .andExpect(jsonPath("$.reporter.email").value(REPORTER))
                .andExpect(jsonPath("$.targetOwner.email").value(OWNER));
    }

    @Test
    void detailShowsDeletedAnswerAndParentEvidence() throws Exception {
        Topic topic = createTopic();
        Answer answer = new Answer();
        answer.setTopic(topic);
        answer.setUser(users.findByEmail(OWNER).orElseThrow());
        answer.setContent("通報時回答");
        answer = answers.saveAndFlush(answer);
        Report report = createReport(ReportTargetType.ANSWER, answer.getId(), REPORTER);
        Long answerId = answer.getId();
        new TransactionTemplate(transactions).executeWithoutResult(tx ->
                answerSnapshots.saveAndFlush(new ReportAnswerSnapshot(reports.findById(report.getId()).orElseThrow(),
                        answers.findById(answerId).orElseThrow(), topics.findById(topic.getId()).orElseThrow(),
                        new TopicImage(users.findByEmail(OWNER).orElseThrow().getId(),
                                "image/png", new byte[] {1, 2, 3}))));
        topic.setDeletedAt(LocalDateTime.now());
        topics.saveAndFlush(topic);
        mvc.perform(get("/api/admin/reports/{id}", report.getId()).with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTargetState").value("DELETED"))
                .andExpect(jsonPath("$.evidence.answerContent").value("通報時回答"))
                .andExpect(jsonPath("$.evidence.parentTopicTitle").value("通報時タイトル"));
    }

    @Test
    void detailShowsProfileEvidenceAfterWithdrawal() throws Exception {
        User owner = users.findByEmail(OWNER).orElseThrow();
        Profile profile = new Profile();
        profile.setUser(owner);
        profile.setBio("通報時の自己紹介");
        profile = profiles.saveAndFlush(profile);
        Report report = createReport(ReportTargetType.PROFILE, profile.getId(), REPORTER);
        Long profileId = profile.getId();
        new TransactionTemplate(transactions).executeWithoutResult(tx ->
                profileSnapshots.saveAndFlush(new ReportProfileSnapshot(
                        reports.findById(report.getId()).orElseThrow(),
                        profiles.findById(profileId).orElseThrow())));
        owner.setAccountStatus(AccountStatus.WITHDRAWN);
        users.saveAndFlush(owner);
        mvc.perform(get("/api/admin/reports/{id}", report.getId()).with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTargetState").value("DELETED"))
                .andExpect(jsonPath("$.evidence.profileDisplayName").value("owner"))
                .andExpect(jsonPath("$.evidence.profileBio").value("通報時の自己紹介"))
                .andExpect(jsonPath("$.evidence.imageAvailable").value(false));
    }

    @ParameterizedTest
    @CsvSource({
            "UNHANDLED,IN_PROGRESS", "UNHANDLED,RESOLVED",
            "IN_PROGRESS,UNHANDLED", "IN_PROGRESS,RESOLVED",
            "RESOLVED,UNHANDLED", "RESOLVED,IN_PROGRESS"
    })
    void allStatusDirectionsPersistExactlyOneHistory(String before, String after) throws Exception {
        Report report = createReport(ReportTargetType.TOPIC, 98L, REPORTER);
        report.setStatus(ReportStatus.valueOf(before));
        reports.saveAndFlush(report);
        mvc.perform(patch("/api/admin/reports/{id}/status", report.getId())
                .with(user(ADMIN).roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + after + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(after));
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            assertThat(reports.findById(report.getId()).orElseThrow().getStatus())
                    .isEqualTo(ReportStatus.valueOf(after));
            var history = logs.findByTargetTypeAndTargetIdOrderByCreatedAtAscIdAsc("REPORT", report.getId());
            assertThat(history).hasSize(1);
            assertThat(history.getFirst().getAdminUser().getId())
                    .isEqualTo(users.findByEmail(ADMIN).orElseThrow().getId());
            assertThat(history.getFirst().getBeforeStatus()).isEqualTo(before);
            assertThat(history.getFirst().getAfterStatus()).isEqualTo(after);
            assertThat(history.getFirst().getResult()).isEqualTo("SUCCESS");
        });
    }

    @Test
    void sameStatusDoesNotUpdateTimestampOrHistory() throws Exception {
        Report report = createReport(ReportTargetType.TOPIC, 98L, REPORTER);
        LocalDateTime before = reports.findById(report.getId()).orElseThrow().getUpdatedAt();
        mvc.perform(patch("/api/admin/reports/{id}/status", report.getId())
                .with(user(ADMIN).roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"UNHANDLED\"}"))
                .andExpect(status().isOk());
        assertThat(reports.findById(report.getId()).orElseThrow().getUpdatedAt()).isEqualTo(before);
        assertThat(logs.count()).isZero();
    }

    @Test
    void historyFailureRollsBackStatusChange() throws Exception {
        Report report = createReport(ReportTargetType.TOPIC, 98L, REPORTER);
        jdbc.execute("ALTER TABLE admin_operation_logs ADD CONSTRAINT ck_report_history_fail "
                + "CHECK (target_type <> 'REPORT')");
        try {
            mvc.perform(patch("/api/admin/reports/{id}/status", report.getId())
                    .with(user(ADMIN).roles("ADMIN")).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\"}"))
                    .andExpect(status().isInternalServerError());
            assertThat(reports.findById(report.getId()).orElseThrow().getStatus())
                    .isEqualTo(ReportStatus.UNHANDLED);
            assertThat(logs.count()).isZero();
        } finally {
            jdbc.execute("ALTER TABLE admin_operation_logs DROP CONSTRAINT ck_report_history_fail");
        }
    }

    @Test
    void legacyReportWithoutOwnerOrSnapshotRemainsVisible() throws Exception {
        Report legacy = createReport(ReportTargetType.TOPIC, 999L, REPORTER);
        legacy.setTargetOwnerUserId(null);
        reports.saveAndFlush(legacy);
        mvc.perform(get("/api/admin/reports/{id}", legacy.getId()).with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTargetState").value("MISSING"))
                .andExpect(jsonPath("$.targetOwnerUserId").doesNotExist())
                .andExpect(jsonPath("$.evidence").doesNotExist());
    }

    @Test
    void reporterCountSupportsAbuseReview() throws Exception {
        Report first = createReport(ReportTargetType.TOPIC, 1L, REPORTER);
        createReport(ReportTargetType.ANSWER, 2L, REPORTER);
        createReport(ReportTargetType.TOPIC, 3L, SECOND);
        mvc.perform(get("/api/admin/reports/{id}", first.getId()).with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reporterReportCount").value(2));
    }

    @Test
    void inactiveAdminCannotReadOrChangeStatus() throws Exception {
        Report report = createReport(ReportTargetType.TOPIC, 1L, REPORTER);
        User admin = users.findByEmail(ADMIN).orElseThrow();
        admin.setAccountStatus(AccountStatus.FROZEN);
        users.saveAndFlush(admin);
        mvc.perform(get("/api/admin/reports/{id}", report.getId()).with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/reports/{id}/status", report.getId())
                .with(user(ADMIN).roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isForbidden());
        assertThat(logs.count()).isZero();
    }

    @Test
    void missingAdminRecordDoesNotChangeReport() throws Exception {
        Report report = createReport(ReportTargetType.TOPIC, 1L, REPORTER);
        mvc.perform(patch("/api/admin/reports/{id}/status", report.getId())
                .with(user("missing@example.com").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isInternalServerError());
        assertThat(reports.findById(report.getId()).orElseThrow().getStatus())
                .isEqualTo(ReportStatus.UNHANDLED);
        assertThat(logs.count()).isZero();
    }

    private User account(String name, String email, String role) {
        User result = new User();
        result.setUsername(name);
        result.setEmail(email);
        result.setPassword("test-password");
        result.setRole(role);
        return result;
    }

    private Report createReport(ReportTargetType type, Long targetId, String reporterEmail) {
        Report result = new Report();
        result.setReporterUser(users.findByEmail(reporterEmail).orElseThrow());
        result.setTargetType(type);
        result.setTargetId(targetId);
        result.setTargetOwnerUserId(users.findByEmail(OWNER).orElseThrow().getId());
        result.setReason(ReportReason.SPAM);
        result.setDetail("detail");
        return reports.saveAndFlush(result);
    }

    private Topic createTopic() {
        Topic result = new Topic();
        result.setUser(users.findByEmail(OWNER).orElseThrow());
        result.setTitle("通報時タイトル");
        result.setQuestion("通報時本文");
        result.setImage("/api/topic-images/11111111-1111-1111-1111-111111111111");
        return topics.saveAndFlush(result);
    }
}
