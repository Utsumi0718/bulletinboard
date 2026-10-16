package com.example.bulletinboard.controller.api;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.example.bulletinboard.exception.handler.GlobalExceptionHandler;
import com.example.bulletinboard.model.*;
import com.example.bulletinboard.repository.*;
import com.example.bulletinboard.security.SecurityConfig;
import com.example.bulletinboard.service.*;
import com.example.bulletinboard.support.TopicImageFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 専用H2・実Security。テストをトランザクションで包まず、コミット後の行を読み直す。 */
@SpringBootTest(classes = ProfileEvidenceIntegrationTest.FlowConfiguration.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:profile-evidence;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.open-in-view=false", "spring.flyway.enabled=false", "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProfileEvidenceIntegrationTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = User.class)
    @EnableJpaRepositories(basePackageClasses = UserRepository.class)
    @Import({ProfileApiController.class, ProfileImageService.class, ProfileImageValidator.class,
        TopicImageValidator.class, ReportApiController.class, ReportService.class, ReportSubmissionService.class,
        AdminReportApiController.class, AdminReportService.class, TopicImageService.class,
        CustomUserDetailsService.class, SecurityConfig.class, GlobalExceptionHandler.class,
        TopicApiController.class, TopicService.class, AnswerApiController.class, AnswerService.class, LikeService.class,
        AccountApiController.class, WithdrawalService.class, WithdrawalSubmissionService.class})
    static class FlowConfiguration { }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired ProfileRepository profiles;
    @Autowired ProfileImageRepository images;
    @Autowired ReportRepository reports;
    @Autowired ReportProfileSnapshotRepository snapshots;
    @Autowired TopicRepository topics;
    @Autowired AnswerRepository answers;
    @Autowired CustomUserDetailsService registration;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    private User owner, reporter, admin;
    private Long profileId;
    private final byte[] original = TopicImageFixtures.image("png");

    @BeforeEach void setUp() {
        // Userだけの旧データを挟み、Profile IDとUser IDが一致する前提を作らない。
        User legacy = account("legacy", "ROLE_USER"); users.saveAndFlush(legacy);
        owner = register("owner", "ROLE_USER");
        reporter = register("reporter", "ROLE_USER");
        admin = register("admin", "ROLE_ADMIN");
        profileId = profiles.findByUserId(owner.getId()).orElseThrow().getId();
        assertThat(profileId).isNotEqualTo(owner.getId());
    }

    @Test void evidenceSurvivesReplacementResetWithdrawalAndOriginalRemoval() throws Exception {
        String first = upload(original);
        report("SPAM").andExpect(status().isCreated()).andExpect(content().json("{\"message\":\"通報を受け付けました。\"}"));
        Long reportId = reportId();
        upload(TopicImageFixtures.image("jpeg"));
        mvc.perform(delete("/api/profiles/me/icon").with(user(owner.getEmail())).with(csrf()))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(post("/api/account/withdraw").with(user(owner.getEmail())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"Password123\",\"confirmed\":true}"))
                .andExpect(status().isNoContent());
        images.deleteById(first.substring(ProfileImageService.URL_PREFIX.length()));
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var stored = snapshots.findById(reportId).orElseThrow();
            assertThat(stored.getImageContent()).containsExactly(original);
            assertThat(stored.getContentType()).isEqualTo("image/png");
            assertThat(reports.findById(reportId)).isPresent();
        });
        evidence(reportId, admin).andExpect(status().isOk()).andExpect(content().bytes(original))
                .andExpect(content().contentType("image/png")).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; sandbox"));
        mvc.perform(get("/api/admin/reports/{id}", reportId).with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.evidence.imageAvailable").value(true))
                .andExpect(jsonPath("$.evidence.evidenceImageUrl").value("/api/admin/reports/" + reportId + "/evidence-image"));
    }

    @Test void defaultProfileCanBeReportedWithoutInventingImageEvidence() throws Exception {
        report("ABUSE").andExpect(status().isCreated());
        assertThat(snapshots.findById(reportId()).orElseThrow().getImageContent()).isNull();
        evidence(reportId(), admin).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/reports/{id}", reportId()).with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(jsonPath("$.evidence.imageAvailable").value(false))
                .andExpect(jsonPath("$.evidence.evidenceImageUrl").isEmpty());
    }

    @Test void changedReasonStillConflictsAndCanReportDoesNotCheckDuplicates() throws Exception {
        upload(original);
        report("SPAM").andExpect(status().isCreated());
        report("ABUSE").andExpect(status().isConflict());
        mvc.perform(get("/api/profiles/{id}", profileId).with(user(reporter.getEmail())))
                .andExpect(jsonPath("$.canReport").value(true));
        assertThat(reports.findById(reportId())).isPresent();
    }

    @Test void concurrentReportsKeepOneReportAndOneSnapshot() throws Exception {
        upload(original);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var calls = List.of(1, 2).stream().map(n -> pool.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return report("SPAM").andReturn().getResponse().getStatus();
            })).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(calls.get(0).get(20, TimeUnit.SECONDS), calls.get(1).get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(reports.findByReporterUserId(reporter.getId(), org.springframework.data.domain.Pageable.unpaged())
                .getTotalElements()).isEqualTo(1);
        assertThat(snapshots.findById(reportId())).isPresent();
    }

    @Test void missingOriginalCannotCreateEvidenceFreeReport() throws Exception {
        String url = upload(original);
        images.deleteById(url.substring(ProfileImageService.URL_PREFIX.length()));
        assertReportFails();
    }

    @ParameterizedTest @ValueSource(strings = {"https://example.invalid/icon.png", "../../private.png"})
    void unmanagedReferenceIsNeverDownloaded(String reference) throws Exception {
        setIcon(reference);
        assertReportFails();
    }

    @Test void anotherUsersOriginalCannotBeUsedAsEvidence() throws Exception {
        ProfileImage foreign = images.saveAndFlush(new ProfileImage(reporter.getId(), "image/png", original));
        setIcon(ProfileImageService.URL_PREFIX + foreign.getId());
        assertReportFails();
    }

    @Test void snapshotFailureRollsBackReport() throws Exception {
        upload(original);
        jdbc.execute("ALTER TABLE report_profile_snapshots ADD CONSTRAINT ck_profile_snapshot_failure CHECK (display_name <> '" + owner.getUsername() + "')");
        try { assertReportFails(); }
        finally { jdbc.execute("ALTER TABLE report_profile_snapshots DROP CONSTRAINT ck_profile_snapshot_failure"); }
    }

    @Test void profileUpdateFailureRollsBackNewOriginalAndKeepsPreviousReference() throws Exception {
        String first = upload(original);
        long count = images.count();
        jdbc.execute("ALTER TABLE profiles ADD CONSTRAINT ck_profile_update_failure CHECK (id <> " + profileId
                + " OR icon IS NULL OR icon = '" + first + "')");
        try {
            put(original).andExpect(status().isInternalServerError());
            assertThat(images.count()).isEqualTo(count);
            assertThat(profiles.findById(profileId).orElseThrow().getIcon()).isEqualTo(first);
        } finally { jdbc.execute("ALTER TABLE profiles DROP CONSTRAINT ck_profile_update_failure"); }
    }

    @Test void originalInsertFailureKeepsProfileUnchanged() throws Exception {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            jdbc.execute("ALTER TABLE profile_images ADD CONSTRAINT ck_profile_image_failure CHECK (owner_user_id <> " + owner.getId() + ")");
            var body = put(original).andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error").value("Internal Server Error"))
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain("profile_images", "owner_user_id", owner.getEmail());
            var errors = appender.list.stream().filter(e -> e.getLevel() == ch.qos.logback.classic.Level.ERROR).toList();
            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst().getFormattedMessage()).startsWith("profile_operation_error reason=FAILED errorType=")
                    .doesNotContain("INSERT", "profile_images", owner.getEmail());
            assertThat(errors.getFirst().getThrowableProxy()).isNull();
            assertThat(profiles.findById(profileId).orElseThrow().getIcon()).isNull();
            assertThat(images.findAll().stream().filter(i -> i.getOwnerUserId().equals(owner.getId()))).isEmpty();
        } finally {
            try { jdbc.execute("ALTER TABLE profile_images DROP CONSTRAINT IF EXISTS ck_profile_image_failure"); }
            finally { logger.detachAppender(appender); appender.stop(); }
        }
    }

    @Test void retiredAndMissingProfileAre404AndDoNotExposeOriginalName() throws Exception {
        owner.setAccountStatus(AccountStatus.WITHDRAWN); users.saveAndFlush(owner);
        var response = mvc.perform(get("/api/profiles/{id}", profileId).with(user(reporter.getEmail())))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain(owner.getUsername(), owner.getEmail());
        mvc.perform(get("/api/profiles/{id}", Long.MAX_VALUE).with(user(reporter.getEmail()))).andExpect(status().isNotFound());
    }

    @Test void topicAnswerAndProfileResponsesExposeCorrectMenuFlags() throws Exception {
        Topic topic = new Topic(); topic.setUser(owner); topic.setTitle("topic"); topic.setQuestion("question");
        topic.setImage("/legacy/image.png"); topic = topics.saveAndFlush(topic);
        Answer answer = new Answer(); answer.setUser(reporter); answer.setTopic(topic); answer.setContent("answer");
        answers.saveAndFlush(answer);
        mvc.perform(get("/api/topics/{id}", topic.getId()).with(user(owner.getEmail())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.canReport").value(false));
        mvc.perform(get("/api/topics/{id}", topic.getId()).with(user(reporter.getEmail())))
                .andExpect(jsonPath("$.canReport").value(true));
        mvc.perform(get("/api/topics/{id}/answers", topic.getId()).with(user(owner.getEmail())))
                .andExpect(jsonPath("$[0].canReport").value(true));
        mvc.perform(get("/api/topics/{id}/answers", topic.getId()).with(user(reporter.getEmail())))
                .andExpect(jsonPath("$[0].canReport").value(false));
        mvc.perform(get("/api/profiles/{id}", profileId).with(user(owner.getEmail())))
                .andExpect(jsonPath("$.id").value(profileId)).andExpect(jsonPath("$.canReport").value(false))
                .andExpect(jsonPath("$.email").doesNotExist()).andExpect(jsonPath("$.userId").doesNotExist());
        mvc.perform(get("/api/profiles/{id}", profileId)).andExpect(status().isUnauthorized());
    }

    @Test void ordinaryUsersAndReporterCannotReadEvidence() throws Exception {
        upload(original); report("SPAM").andExpect(status().isCreated());
        evidence(reportId(), reporter).andExpect(status().isForbidden());
        evidence(reportId(), owner).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/reports/{id}/evidence-image", reportId())).andExpect(status().isUnauthorized());
    }

    @Test void originalVisibilityFollowsCurrentReferenceAndOwnerStatus() throws Exception {
        String first = upload(original);
        mvc.perform(get(first).with(user(reporter.getEmail()))).andExpect(status().isOk()).andExpect(content().bytes(original));
        upload(TopicImageFixtures.image("jpeg"));
        mvc.perform(get(first).with(user(reporter.getEmail()))).andExpect(status().isNotFound());
        mvc.perform(get(first).with(user(owner.getEmail()))).andExpect(status().isOk());
        String current = profiles.findById(profileId).orElseThrow().getIcon();
        owner.setAccountStatus(AccountStatus.WITHDRAWN); users.saveAndFlush(owner);
        mvc.perform(get(current).with(user(reporter.getEmail()))).andExpect(status().isNotFound());
    }

    @ParameterizedTest @CsvSource({"PUT,false", "PUT,true", "DELETE,false", "DELETE,true"})
    void iconMutationsRequireValidCsrfAndAuthentication(String method, boolean invalid) throws Exception {
        var request = method.equals("PUT") ? multipart("/api/profiles/me/icon").file(file(original))
                .with(r -> { r.setMethod("PUT"); return r; }) : delete("/api/profiles/me/icon");
        request.with(user(owner.getEmail()));
        if (invalid) request.with(csrf().useInvalidToken());
        mvc.perform(request).andExpect(status().isForbidden());
        var anonymous = method.equals("PUT") ? multipart("/api/profiles/me/icon").file(file(original))
                .with(r -> { r.setMethod("PUT"); return r; }) : delete("/api/profiles/me/icon");
        mvc.perform(anonymous.with(csrf())).andExpect(status().isUnauthorized());
        assertThat(profiles.findById(profileId).orElseThrow().getIcon()).isNull();
    }

    @Test void invalidImageReturnsFieldErrorWithoutChangingProfile() throws Exception {
        put(new byte[] {1, 2, 3}).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.file").exists());
        assertThat(profiles.findById(profileId).orElseThrow().getIcon()).isNull();
    }

    @ParameterizedTest @ValueSource(strings = {"FROZEN", "WITHDRAWN"})
    void inactiveOwnerCannotChangeOrResetIcon(String status) throws Exception {
        String first = upload(original);
        owner.setAccountStatus(AccountStatus.valueOf(status)); users.saveAndFlush(owner);
        put(original).andExpect(status().isForbidden());
        mvc.perform(delete("/api/profiles/me/icon").with(user(owner.getEmail())).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(profiles.findById(profileId).orElseThrow().getIcon()).isEqualTo(first);
    }

    @Test void frozenOwnersConfiguredImageCanStillBeReported() throws Exception {
        upload(original);
        owner.setAccountStatus(AccountStatus.FROZEN); users.saveAndFlush(owner);
        mvc.perform(get("/api/profiles/{id}", profileId).with(user(reporter.getEmail())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.canReport").value(true));
        report("SPAM").andExpect(status().isCreated());
        evidence(reportId(), admin).andExpect(status().isOk()).andExpect(content().bytes(original));
    }

    @ParameterizedTest @ValueSource(strings = {"topics", "answers", "profiles"})
    void missingReasonReturnsFieldErrorForEveryTarget(String type) throws Exception {
        mvc.perform(post("/api/" + type + "/" + profileId + "/reports").with(user(reporter.getEmail())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.reason").exists());
    }

    private ResultActions put(byte[] bytes) throws Exception {
        return mvc.perform(multipart("/api/profiles/me/icon").file(file(bytes))
                .with(r -> { r.setMethod("PUT"); return r; }).with(user(owner.getEmail())).with(csrf()));
    }
    private String upload(byte[] bytes) throws Exception {
        var response = put(bytes).andExpect(status().isOk()).andExpect(jsonPath("$.canReport").value(false)).andReturn();
        return mapper.readTree(response.getResponse().getContentAsString()).path("icon").asText();
    }
    private ResultActions report(String reason) throws Exception {
        return mvc.perform(post("/api/profiles/{id}/reports", profileId).with(user(reporter.getEmail())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + reason + "\"}"));
    }
    private Long reportId() {
        return reports.findByReporterUserId(reporter.getId(), org.springframework.data.domain.Pageable.unpaged())
                .getContent().stream().filter(r -> r.getTargetId().equals(profileId)).findFirst().orElseThrow().getId();
    }
    private ResultActions evidence(Long id, User viewer) throws Exception {
        return mvc.perform(get("/api/admin/reports/{id}/evidence-image", id).with(user(viewer.getEmail())
                .roles(viewer.getRole().equals("ROLE_ADMIN") ? "ADMIN" : "USER")));
    }
    private void setIcon(String icon) {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> profiles.findById(profileId).orElseThrow().setIcon(icon));
    }
    private void assertReportFails() throws Exception {
        report("SPAM").andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("通報を受け付けられませんでした。時間をおいて再度お試しください。"));
        assertThat(reports.findByReporterUserId(reporter.getId(), org.springframework.data.domain.Pageable.unpaged()).isEmpty()).isTrue();
    }
    private MockMultipartFile file(byte[] bytes) { return new MockMultipartFile("file", "image.png", "image/png", bytes); }
    private User register(String name, String role) { User u = account(name, role); registration.registerUser(u); return u; }
    private User account(String name, String role) {
        String suffix = UUID.randomUUID().toString();
        User u = new User(); u.setUsername(name + suffix); u.setEmail(name + suffix + "@example.com");
        u.setPassword("Password123"); u.setRole(role); u.setAccountStatus(AccountStatus.ACTIVE); return u;
    }
}
