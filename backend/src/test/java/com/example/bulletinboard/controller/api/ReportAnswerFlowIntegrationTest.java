package com.example.bulletinboard.controller.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import com.example.bulletinboard.model.Report;
import com.example.bulletinboard.model.ReportStatus;
import com.example.bulletinboard.model.ReportTargetType;
import com.example.bulletinboard.model.Topic;
import com.example.bulletinboard.model.TopicImage;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.AnswerRepository;
import com.example.bulletinboard.repository.ReportAnswerSnapshotRepository;
import com.example.bulletinboard.repository.ReportRepository;
import com.example.bulletinboard.repository.ReportTopicSnapshotRepository;
import com.example.bulletinboard.repository.TopicImageRepository;
import com.example.bulletinboard.repository.TopicRepository;
import com.example.bulletinboard.repository.UserRepository;
import com.example.bulletinboard.security.SecurityConfig;
import com.example.bulletinboard.service.CustomUserDetailsService;
import com.example.bulletinboard.service.AnswerService;
import com.example.bulletinboard.service.LikeService;
import com.example.bulletinboard.service.ReportService;
import com.example.bulletinboard.service.ReportSubmissionService;
import com.example.bulletinboard.service.TopicImageService;

/** H2で回答通報の受付・保持を各Serviceのトランザクション終了後に確認する。 */
@SpringBootTest(classes = ReportAnswerFlowIntegrationTest.FlowConfiguration.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:report-answer-flow;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=false", "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReportAnswerFlowIntegrationTest {
    private static final String TOPIC_OWNER = "topic-owner@example.com";
    private static final String ANSWER_OWNER = "answer-owner@example.com";
    private static final String REPORTER = "reporter@example.com";
    private static final String SECOND = "second@example.com";
    private static final String ADMIN = "admin@example.com";
    private static final byte[] IMAGE = {1, 2, 3, 4};

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = User.class)
    @EnableJpaRepositories(basePackageClasses = UserRepository.class)
    @Import({ReportApiController.class, ReportService.class, ReportSubmissionService.class,
            AnswerService.class, LikeService.class,
            TopicImageService.class, CustomUserDetailsService.class, SecurityConfig.class,
            GlobalExceptionHandler.class})
    static class FlowConfiguration { }

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TopicRepository topics;
    @Autowired AnswerRepository answers;
    @Autowired TopicImageRepository images;
    @Autowired ReportRepository reports;
    @Autowired ReportTopicSnapshotRepository topicSnapshots;
    @Autowired ReportAnswerSnapshotRepository answerSnapshots;
    @Autowired PlatformTransactionManager transactions;
    @Autowired AnswerService answerService;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        answerSnapshots.deleteAll();
        topicSnapshots.deleteAll();
        reports.deleteAll();
        answers.deleteAll();
        topics.deleteAll();
        images.deleteAll();
        users.deleteAll();
        users.saveAndFlush(account("topic-owner", TOPIC_OWNER, "ROLE_USER"));
        users.saveAndFlush(account("answer-owner", ANSWER_OWNER, "ROLE_USER"));
        users.saveAndFlush(account("reporter", REPORTER, "ROLE_USER"));
        users.saveAndFlush(account("second", SECOND, "ROLE_USER"));
        users.saveAndFlush(account("admin", ADMIN, "ROLE_ADMIN"));
    }

    @Test
    void keepsAnswerAndParentEvidenceAfterEditAndDeletion() throws Exception {
        Topic topic = createTopic();
        Answer answer = createAnswer(topic);
        assertThat(report(answer.getId(), REPORTER, "INAPPROPRIATE", "確認してください")).isEqualTo(201);
        Report saved = readReport(ReportTargetType.ANSWER);
        assertThat(saved.getTargetId()).isEqualTo(answer.getId());
        assertThat(saved.getTargetOwnerUserId()).isEqualTo(users.findByEmail(ANSWER_OWNER).orElseThrow().getId());
        assertThat(saved.getReporterUser().getId()).isEqualTo(users.findByEmail(REPORTER).orElseThrow().getId());
        assertThat(saved.getStatus()).isEqualTo(ReportStatus.UNHANDLED);

        answerService.updateAnswer(answer.getId(), ANSWER_OWNER, "編集後の回答");
        answerService.deleteAnswer(answer.getId(), ANSWER_OWNER, false);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            Topic parent = topics.findById(topic.getId()).orElseThrow();
            parent.setTitle("編集後のタイトル");
            parent.setQuestion("編集後の本文");
            parent.setDeletedAt(LocalDateTime.now());
            topics.saveAndFlush(parent);
        });
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var snapshot = answerSnapshots.findById(saved.getId()).orElseThrow();
            assertThat(snapshot.getAnswerContent()).isEqualTo("通報時の回答");
            assertThat(snapshot.getTopicId()).isEqualTo(topic.getId());
            assertThat(snapshot.getTopicTitle()).isEqualTo("通報時のタイトル");
            assertThat(snapshot.getTopicQuestion()).isEqualTo("通報時の本文");
            assertThat(snapshot.getImageContent()).isEqualTo(IMAGE);
        });
        mvc.perform(get("/api/admin/reports/{id}/evidence-image", saved.getId())
                .with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(IMAGE));
        mvc.perform(get("/api/admin/reports/{id}/evidence-image", saved.getId()).with(user(REPORTER)))
                .andExpect(status().isForbidden());
        assertThat(reports.count()).isEqualTo(1);
        assertThat(answerSnapshots.count()).isEqualTo(1);
    }

    @Test
    void sameNumericIdForTopicAndAnswerIsSeparateTarget() throws Exception {
        Topic topic = createTopic();
        Answer answer = createAnswer(topic);
        assertThat(answer.getId()).isEqualTo(topic.getId());
        assertThat(postReport("/api/topics/" + topic.getId() + "/reports", REPORTER, "SPAM", null))
                .isEqualTo(201);
        assertThat(report(answer.getId(), REPORTER, "SPAM", null)).isEqualTo(201);
        assertThat(reports.count()).isEqualTo(2);
        assertThat(reports.findAll()).extracting(Report::getTargetType)
                .containsExactlyInAnyOrder(ReportTargetType.TOPIC, ReportTargetType.ANSWER);
    }

    @Test
    void deletedParentMakesAnswerUnreportable() throws Exception {
        Topic topic = createTopic();
        Answer answer = createAnswer(topic);
        topic.setDeletedAt(LocalDateTime.now());
        topics.saveAndFlush(topic);
        assertThat(report(answer.getId(), REPORTER, "SPAM", null)).isEqualTo(404);
        assertThat(reports.count()).isZero();
    }

    @Test
    void deletedAnswerIsUnreportable() throws Exception {
        Answer answer = createAnswer(createTopic());
        answer.setDeletedAt(LocalDateTime.now());
        answers.saveAndFlush(answer);
        assertThat(report(answer.getId(), REPORTER, "SPAM", null)).isEqualTo(404);
        assertThat(reports.count()).isZero();
    }

    @Test
    void missingAnswerIs404() throws Exception {
        assertThat(report(999_999L, REPORTER, "SPAM", null)).isEqualTo(404);
    }

    @Test
    void answerOwnerCannotReportOwnAnswer() throws Exception {
        Answer answer = createAnswer(createTopic());
        assertThat(report(answer.getId(), ANSWER_OWNER, "SPAM", null)).isEqualTo(403);
        assertThat(reports.count()).isZero();
    }

    @Test
    void duplicateStillConflictsAfterResolved() throws Exception {
        Answer answer = createAnswer(createTopic());
        assertThat(report(answer.getId(), REPORTER, "SPAM", null)).isEqualTo(201);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            Report first = reports.findById(readReport(ReportTargetType.ANSWER).getId()).orElseThrow();
            first.setStatus(ReportStatus.RESOLVED);
            reports.saveAndFlush(first);
        });
        assertThat(report(answer.getId(), REPORTER, "OTHER", "再通報")).isEqualTo(409);
        assertThat(reports.count()).isEqualTo(1);
    }

    @Test
    void differentReportersCanReportSameAnswer() throws Exception {
        Answer answer = createAnswer(createTopic());
        assertThat(report(answer.getId(), REPORTER, "SPAM", null)).isEqualTo(201);
        assertThat(report(answer.getId(), SECOND, "ABUSE", null)).isEqualTo(201);
        assertThat(reports.count()).isEqualTo(2);
    }

    @Test
    void parentWithoutManagedImageFailsWithoutReport() throws Exception {
        Topic topic = createTopic();
        topic.setImage("/images/legacy.png");
        topics.saveAndFlush(topic);
        Answer answer = createAnswer(topic);
        assertThat(report(answer.getId(), REPORTER, "SPAM", null)).isEqualTo(500);
        assertThat(reports.count()).isZero();
        assertThat(answerSnapshots.count()).isZero();
    }

    @Test
    void snapshotInsertFailureRollsBackAnswerReport() throws Exception {
        Answer answer = createAnswer(createTopic());
        jdbc.execute("ALTER TABLE report_answer_snapshots ADD CONSTRAINT ck_answer_snapshot_fail "
                + "CHECK (byte_size < 0)");
        try {
            assertThat(report(answer.getId(), REPORTER, "SPAM", null)).isEqualTo(500);
            assertThat(reports.count()).isZero();
            assertThat(answerSnapshots.count()).isZero();
        } finally {
            jdbc.execute("ALTER TABLE report_answer_snapshots DROP CONSTRAINT ck_answer_snapshot_fail");
        }
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"FROZEN", "WITHDRAWN"})
    void inactiveReporterCannotReport(AccountStatus status) throws Exception {
        Answer answer = createAnswer(createTopic());
        User reporter = users.findByEmail(REPORTER).orElseThrow();
        reporter.setAccountStatus(status);
        users.saveAndFlush(reporter);
        assertThat(report(answer.getId(), REPORTER, "SPAM", null)).isEqualTo(403);
        assertThat(reports.count()).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"FROZEN", "WITHDRAWN"})
    void publicAnswerFromInactiveOwnerCanBeReported(AccountStatus status) throws Exception {
        Answer answer = createAnswer(createTopic());
        User owner = users.findByEmail(ANSWER_OWNER).orElseThrow();
        owner.setAccountStatus(status);
        users.saveAndFlush(owner);
        assertThat(report(answer.getId(), REPORTER, "SPAM", null)).isEqualTo(201);
    }

    @Test
    void concurrentSameReporterKeepsExactlyOneAnswerReport() throws Exception {
        Answer answer = createAnswer(createTopic());
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var calls = List.of(1, 2).stream().map(number -> pool.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return report(answer.getId(), REPORTER, "SPAM", null);
            })).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(calls.get(0).get(), calls.get(1).get()))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(reports.count()).isEqualTo(1);
        assertThat(answerSnapshots.count()).isEqualTo(1);
    }

    private User account(String name, String email, String role) {
        User result = new User();
        result.setUsername(name);
        result.setEmail(email);
        result.setPassword("test-only");
        result.setRole(role);
        result.setAccountStatus(AccountStatus.ACTIVE);
        return result;
    }

    private Topic createTopic() {
        User owner = users.findByEmail(TOPIC_OWNER).orElseThrow();
        TopicImage image = images.saveAndFlush(new TopicImage(owner.getId(), "image/png", IMAGE));
        Topic topic = new Topic();
        topic.setUser(owner);
        topic.setTitle("通報時のタイトル");
        topic.setQuestion("通報時の本文");
        topic.setImage(TopicImageService.URL_PREFIX + image.getId());
        return topics.saveAndFlush(topic);
    }

    private Answer createAnswer(Topic topic) {
        Answer answer = new Answer();
        answer.setTopic(topic);
        answer.setUser(users.findByEmail(ANSWER_OWNER).orElseThrow());
        answer.setContent("通報時の回答");
        return answers.saveAndFlush(answer);
    }

    private int report(Long id, String email, String reason, String detail) throws Exception {
        return postReport("/api/answers/" + id + "/reports", email, reason, detail);
    }

    private int postReport(String path, String email, String reason, String detail) throws Exception {
        String json = "{\"reason\":\"" + reason + "\"" +
                (detail == null ? "}" : ",\"detail\":\"" + detail + "\"}");
        return mvc.perform(post(path).with(user(email)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json))
                .andReturn().getResponse().getStatus();
    }

    private Report readReport(ReportTargetType type) {
        return new TransactionTemplate(transactions).execute(tx -> reports.findAll().stream()
                .filter(report -> report.getTargetType() == type).findFirst().orElseThrow());
    }
}
