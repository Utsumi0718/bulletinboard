package com.example.bulletinboard.controller.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.bulletinboard.exception.handler.GlobalExceptionHandler;
import com.example.bulletinboard.model.AccountStatus;
import com.example.bulletinboard.model.Report;
import com.example.bulletinboard.model.ReportStatus;
import com.example.bulletinboard.model.ReportTargetType;
import com.example.bulletinboard.model.Topic;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.ReportRepository;
import com.example.bulletinboard.repository.ReportTopicSnapshotRepository;
import com.example.bulletinboard.repository.TopicImageRepository;
import com.example.bulletinboard.repository.TopicRepository;
import com.example.bulletinboard.repository.UserRepository;
import com.example.bulletinboard.security.SecurityConfig;
import com.example.bulletinboard.service.CustomUserDetailsService;
import com.example.bulletinboard.service.ReportService;
import com.example.bulletinboard.service.ReportSubmissionService;
import com.example.bulletinboard.service.TopicImageService;
import com.example.bulletinboard.service.TopicImageValidator;
import com.example.bulletinboard.service.TopicService;
import com.example.bulletinboard.support.TopicImageFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 専用H2で実Controller・Service・Repository・Securityを使用し、Service終了後にDBを読み直す。 */
@SpringBootTest(classes = ReportFlowIntegrationTest.FlowConfiguration.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:report-topic-flow;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=false", "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReportFlowIntegrationTest {
    private static final String OWNER = "owner@example.com";
    private static final String REPORTER = "reporter@example.com";
    private static final String SECOND = "second@example.com";
    private static final String ADMIN = "admin@example.com";

    @TestComponent
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = User.class)
    @EnableJpaRepositories(basePackageClasses = UserRepository.class)
    @Import({TopicApiController.class, TopicImageApiController.class, ReportApiController.class,
            TopicService.class, TopicImageService.class, TopicImageValidator.class,
            ReportService.class, ReportSubmissionService.class, CustomUserDetailsService.class,
            SecurityConfig.class, GlobalExceptionHandler.class})
    static class FlowConfiguration { }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired TopicRepository topics;
    @Autowired TopicImageRepository images;
    @Autowired ReportRepository reports;
    @Autowired ReportTopicSnapshotRepository snapshots;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach
    void setUp() {
        snapshots.deleteAll();
        reports.deleteAll();
        topics.deleteAll();
        images.deleteAll();
        users.deleteAll();
        users.saveAndFlush(userAccount("owner", OWNER, "ROLE_USER"));
        users.saveAndFlush(userAccount("reporter", REPORTER, "ROLE_USER"));
        users.saveAndFlush(userAccount("second", SECOND, "ROLE_USER"));
        users.saveAndFlush(userAccount("admin", ADMIN, "ROLE_ADMIN"));
    }

    @Test
    void keepsExactEvidenceAfterTopicEditAndDeleteAndLimitsAdminRead() throws Exception {
        byte[] original = TopicImageFixtures.image("png");
        String firstImage = upload(OWNER, "png");
        long id = create(firstImage, "通報時タイトル", "通報時本文");
        assertThat(report(id, REPORTER, "INAPPROPRIATE", "内容を確認してください")).isEqualTo(201);

        Report saved = readReport();
        assertThat(saved.getTargetType()).isEqualTo(ReportTargetType.TOPIC);
        assertThat(saved.getTargetId()).isEqualTo(id);
        assertThat(saved.getReporterUser().getId()).isEqualTo(users.findByEmail(REPORTER).orElseThrow().getId());
        assertThat(saved.getTargetOwnerUserId()).isEqualTo(users.findByEmail(OWNER).orElseThrow().getId());
        assertThat(saved.getStatus()).isEqualTo(ReportStatus.UNHANDLED);
        assertThat(readSnapshotBytes(saved.getId())).isEqualTo(original);

        String replacement = upload(OWNER, "jpeg");
        mvc.perform(put("/api/topics/{id}", id).with(user(OWNER)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(topicBody(replacement, "変更後", "変更後の本文")))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/topics/{id}", id).with(user(OWNER)).with(csrf()))
                .andExpect(status().isNoContent());

        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var snapshot = snapshots.findById(saved.getId()).orElseThrow();
            assertThat(snapshot.getTitle()).isEqualTo("通報時タイトル");
            assertThat(snapshot.getQuestion()).isEqualTo("通報時本文");
            assertThat(snapshot.getImageContent()).isEqualTo(original);
            assertThat(topics.findById(id).orElseThrow().getDeletedAt()).isNotNull();
        });
        String evidencePath = "/api/admin/reports/" + saved.getId() + "/evidence-image";
        mvc.perform(get(evidencePath).with(user(ADMIN).roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().contentType("image/png")).andExpect(content().bytes(original));
        mvc.perform(get(evidencePath).with(user(REPORTER))).andExpect(status().isForbidden());
        mvc.perform(get(evidencePath)).andExpect(status().isUnauthorized());
        assertThat(reports.count()).isEqualTo(1);
        assertThat(snapshots.count()).isEqualTo(1);
    }

    @Test
    void twoDifferentReportersMayReportSameTopic() throws Exception {
        long id = create(upload(OWNER, "png"), "二人が確認", "本文");
        assertThat(report(id, REPORTER, "ABUSE", null)).isEqualTo(201);
        assertThat(report(id, SECOND, "SPAM", null)).isEqualTo(201);
        assertThat(reports.count()).isEqualTo(2);
        assertThat(snapshots.count()).isEqualTo(2);
    }

    @Test
    void duplicateRemains409AfterResolved() throws Exception {
        long id = create(upload(OWNER, "png"), "重複", "本文");
        assertThat(report(id, REPORTER, "SPAM", null)).isEqualTo(201);
        Long reportId = readReport().getId();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            Report existing = reports.findById(reportId).orElseThrow();
            existing.setStatus(ReportStatus.RESOLVED);
            reports.saveAndFlush(existing);
        });
        assertThat(report(id, REPORTER, "OTHER", "再送" )).isEqualTo(409);
        assertThat(reports.count()).isEqualTo(1);
    }

    @Test
    void databaseUniqueConstraintRejectsSameReporterAndTarget() throws Exception {
        long id = create(upload(OWNER, "png"), "DB重複", "本文");
        assertThat(report(id, REPORTER, "SPAM", null)).isEqualTo(201);
        Long reporterId = users.findByEmail(REPORTER).orElseThrow().getId();

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO reports (reporter_user_id, target_type, target_id, reason, status, created_at, updated_at) "
                        + "VALUES (?, 'TOPIC', ?, 'SPAM', 'UNHANDLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                reporterId, id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(reports.count()).isEqualTo(1);
    }

    @Test
    void nonexistentOrAlreadyDeletedTopicReturns404() throws Exception {
        assertThat(report(999_999L, REPORTER, "SPAM", null)).isEqualTo(404);
        long id = create(upload(OWNER, "png"), "削除", "本文");
        mvc.perform(delete("/api/topics/{id}", id).with(user(OWNER)).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(report(id, REPORTER, "SPAM", null)).isEqualTo(404);
        assertThat(reports.count()).isZero();
    }

    @Test
    void selfReportIsForbiddenAndWritesNothing() throws Exception {
        long id = create(upload(OWNER, "png"), "自分", "本文");
        assertThat(report(id, OWNER, "SPAM", null)).isEqualTo(403);
        assertThat(reports.count()).isZero();
        assertThat(snapshots.count()).isZero();
    }

    @Test
    void legacyUrlWithoutImageBytesFailsWithoutCreatingReport() throws Exception {
        Topic topic = new Topic();
        topic.setUser(users.findByEmail(OWNER).orElseThrow());
        topic.setTitle("旧画像");
        topic.setQuestion("本文");
        topic.setImage("/images/legacy.png");
        long id = topics.saveAndFlush(topic).getId();
        assertThat(report(id, REPORTER, "SPAM", null)).isEqualTo(500);
        assertThat(reports.count()).isZero();
        assertThat(snapshots.count()).isZero();
    }

    @Test
    void reportInsertFailureLeavesNoSnapshot() throws Exception {
        long id = create(upload(OWNER, "png"), "保存拒否", "本文");
        jdbc.execute("ALTER TABLE reports ADD CONSTRAINT ck_report_test_fail CHECK (target_id <> " + id + ")");
        try {
            assertThat(report(id, REPORTER, "SPAM", null)).isEqualTo(500);
            assertThat(reports.count()).isZero();
            assertThat(snapshots.count()).isZero();
        } finally {
            jdbc.execute("ALTER TABLE reports DROP CONSTRAINT ck_report_test_fail");
        }
    }

    @Test
    void snapshotInsertFailureRollsBackReport() throws Exception {
        long id = create(upload(OWNER, "png"), "証拠保存拒否", "本文");
        jdbc.execute("ALTER TABLE report_topic_snapshots ADD CONSTRAINT ck_snapshot_test_fail CHECK (byte_size < 0)");
        try {
            assertThat(report(id, REPORTER, "SPAM", null)).isEqualTo(500);
            assertThat(reports.count()).isZero();
            assertThat(snapshots.count()).isZero();
        } finally {
            jdbc.execute("ALTER TABLE report_topic_snapshots DROP CONSTRAINT ck_snapshot_test_fail");
        }
    }

    @Test
    void concurrentDuplicateSubmissionsKeepExactlyOneReport() throws Exception {
        long id = create(upload(OWNER, "png"), "同時通報", "本文");
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var calls = List.of(1, 2).stream().map(number -> pool.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return report(id, REPORTER, "SPAM", null);
            })).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(calls.get(0).get(), calls.get(1).get()))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(reports.count()).isEqualTo(1);
        assertThat(snapshots.count()).isEqualTo(1);
    }

    private User userAccount(String name, String email, String role) {
        User result = new User();
        result.setUsername(name);
        result.setEmail(email);
        result.setPassword("test-only-not-used-for-login");
        result.setRole(role);
        result.setAccountStatus(AccountStatus.ACTIVE);
        return result;
    }

    private String upload(String email, String format) throws Exception {
        var result = mvc.perform(multipart("/api/topic-images")
                .file(new MockMultipartFile("file", "picture." + format, "image/" + format,
                        TopicImageFixtures.image(format)))
                .with(user(email)).with(csrf())).andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).path("url").asText();
    }

    private long create(String image, String title, String question) throws Exception {
        var result = mvc.perform(post("/api/topics").with(user(OWNER)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(topicBody(image, title, question)))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
    }

    private String topicBody(String image, String title, String question) throws Exception {
        return mapper.writeValueAsString(Map.of("title", title, "question", question, "image", image));
    }

    private int report(long id, String email, String reason, String detail) throws Exception {
        String body = mapper.writeValueAsString(detail == null ? Map.of("reason", reason) :
                Map.of("reason", reason, "detail", detail));
        return mvc.perform(post("/api/topics/{id}/reports", id).with(user(email)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();
    }

    private Report readReport() {
        return new TransactionTemplate(transactions).execute(tx -> reports.findAll().getFirst());
    }

    private byte[] readSnapshotBytes(Long reportId) {
        return new TransactionTemplate(transactions).execute(tx ->
                snapshots.findById(reportId).orElseThrow().getImageContent());
    }
}
