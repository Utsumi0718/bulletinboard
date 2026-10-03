package com.example.bulletinboard.controller.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.example.bulletinboard.model.Profile;
import com.example.bulletinboard.model.Report;
import com.example.bulletinboard.model.ReportStatus;
import com.example.bulletinboard.model.ReportTargetType;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.ProfileRepository;
import com.example.bulletinboard.repository.ReportProfileSnapshotRepository;
import com.example.bulletinboard.repository.ReportRepository;
import com.example.bulletinboard.repository.UserRepository;
import com.example.bulletinboard.security.SecurityConfig;
import com.example.bulletinboard.service.CustomUserDetailsService;
import com.example.bulletinboard.service.ReportService;
import com.example.bulletinboard.service.ReportSubmissionService;
import com.example.bulletinboard.service.TopicImageService;

/** H2で登録とプロフィール通報を、各Serviceのコミット後に検証する。 */
@SpringBootTest(classes = ReportProfileFlowIntegrationTest.FlowConfiguration.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:report-profile-flow;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=false", "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReportProfileFlowIntegrationTest {
    private static final String OWNER = "owner@example.com";
    private static final String REPORTER = "reporter@example.com";
    private static final String SECOND = "second@example.com";

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = User.class)
    @EnableJpaRepositories(basePackageClasses = UserRepository.class)
    @Import({ReportApiController.class, ReportService.class, ReportSubmissionService.class,
            TopicImageService.class, CustomUserDetailsService.class, SecurityConfig.class,
            GlobalExceptionHandler.class})
    static class FlowConfiguration { }

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ProfileRepository profiles;
    @Autowired ReportRepository reports;
    @Autowired ReportProfileSnapshotRepository snapshots;
    @Autowired CustomUserDetailsService registration;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    private Long profileId;

    @BeforeEach
    void setUp() {
        snapshots.deleteAll();
        reports.deleteAll();
        profiles.deleteAll();
        users.deleteAll();
        // 既存のProfile不足ユーザーを再現し、users.idとprofiles.idをずらす。
        users.saveAndFlush(account("legacy", "legacy@example.com"));
        registration.registerUser(account("reporter", REPORTER));
        registration.registerUser(account("second", SECOND));
        registration.registerUser(account("owner", OWNER));
        profileId = profiles.findByUserId(users.findByEmail(OWNER).orElseThrow().getId()).orElseThrow().getId();
    }

    @Test
    void registrationCreatesExactlyOneEmptyProfileWithDistinctKeys() {
        User owner = users.findByEmail(OWNER).orElseThrow();
        Profile profile = profiles.findByUserId(owner.getId()).orElseThrow();
        assertThat(profile.getIcon()).isNull();
        assertThat(profile.getBio()).isNull();
        assertThat(profiles.count()).isEqualTo(3);
        assertThat(profile.getId()).isNotEqualTo(owner.getId());
    }

    @Test
    void databaseRejectsSecondProfileForSameUser() {
        Profile duplicate = new Profile();
        duplicate.setUser(users.findByEmail(OWNER).orElseThrow());
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> profiles.saveAndFlush(duplicate));
        assertThat(profiles.findByUserId(duplicate.getUser().getId())).isPresent();
        assertThat(profiles.count()).isEqualTo(3);
    }

    @Test
    void failedProfileInsertRollsBackUserRegistration() {
        jdbc.update("UPDATE profiles SET bio = 'existing' WHERE bio IS NULL");
        jdbc.execute("ALTER TABLE profiles ADD CONSTRAINT ck_profile_insert_fail CHECK (bio IS NOT NULL)");
        try {
            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                    () -> registration.registerUser(account("rollback", "rollback@example.com")));
            assertThat(users.findByEmail("rollback@example.com")).isEmpty();
            assertThat(profiles.count()).isEqualTo(3);
        } finally {
            jdbc.execute("ALTER TABLE profiles DROP CONSTRAINT ck_profile_insert_fail");
        }
    }

    @Test
    void reportStoresProfileIdOwnerAndSnapshotAfterWithdrawal() throws Exception {
        Profile profile = profiles.findById(profileId).orElseThrow();
        profile.setBio("通報時の自己紹介");
        profiles.saveAndFlush(profile);
        assertThat(report(profileId, REPORTER, "ABUSE", "確認してください")).isEqualTo(201);
        Report saved = reports.findAll().getFirst();
        assertThat(saved.getTargetType()).isEqualTo(ReportTargetType.PROFILE);
        assertThat(saved.getTargetId()).isEqualTo(profileId);
        assertThat(saved.getTargetOwnerUserId()).isEqualTo(users.findByEmail(OWNER).orElseThrow().getId());
        assertThat(saved.getReporterUser().getId()).isEqualTo(users.findByEmail(REPORTER).orElseThrow().getId());
        assertThat(saved.getStatus()).isEqualTo(ReportStatus.UNHANDLED);

        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            Profile changed = profiles.findById(profileId).orElseThrow();
            changed.setBio("変更後");
            changed.getUser().setUsername("changed-owner");
            changed.getUser().setAccountStatus(AccountStatus.WITHDRAWN);
        });
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var snapshot = snapshots.findById(saved.getId()).orElseThrow();
            assertThat(snapshot.getDisplayName()).isEqualTo("owner");
            assertThat(snapshot.getBio()).isEqualTo("通報時の自己紹介");
            assertThat(snapshot.getImageContent()).isNull();
            assertThat(reports.findById(saved.getId())).isPresent();
        });
        assertThat(report(profileId, SECOND, "SPAM", null)).isEqualTo(404);
        assertThat(report(profileId, REPORTER, "SPAM", null)).isEqualTo(404);
    }

    @Test
    void ownerCannotReportOwnProfile() throws Exception {
        assertThat(report(profileId, OWNER, "SPAM", null)).isEqualTo(403);
        assertThat(reports.count()).isZero();
    }

    @Test
    void duplicateRemainsConflictAfterStatusChange() throws Exception {
        assertThat(report(profileId, REPORTER, "SPAM", null)).isEqualTo(201);
        Report saved = reports.findAll().getFirst();
        saved.setStatus(ReportStatus.RESOLVED);
        reports.saveAndFlush(saved);
        assertThat(report(profileId, REPORTER, "SPAM", null)).isEqualTo(409);
        assertThat(reports.count()).isEqualTo(1);
        assertThat(snapshots.count()).isEqualTo(1);
    }

    @Test
    void userIdIsNotAcceptedAsProfileId() throws Exception {
        Long ownerId = users.findByEmail(OWNER).orElseThrow().getId();
        assertThat(ownerId).isNotEqualTo(profileId);
        assertThat(report(ownerId, REPORTER, "SPAM", null)).isEqualTo(404);
        assertThat(reports.count()).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"ACTIVE", "FROZEN"})
    void visibleOwnerCanBeReported(AccountStatus status) throws Exception {
        User owner = users.findByEmail(OWNER).orElseThrow();
        owner.setAccountStatus(status);
        users.saveAndFlush(owner);
        assertThat(report(profileId, REPORTER, "SPAM", null)).isEqualTo(201);
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"FROZEN", "WITHDRAWN"})
    void inactiveReporterCannotReport(AccountStatus status) throws Exception {
        User reporter = users.findByEmail(REPORTER).orElseThrow();
        reporter.setAccountStatus(status);
        users.saveAndFlush(reporter);
        assertThat(report(profileId, REPORTER, "SPAM", null)).isEqualTo(403);
        assertThat(reports.count()).isZero();
    }

    @Test
    void missingAndWithdrawnProfilesAreNotReportable() throws Exception {
        assertThat(report(Long.MAX_VALUE, REPORTER, "SPAM", null)).isEqualTo(404);
        User owner = users.findByEmail(OWNER).orElseThrow();
        owner.setAccountStatus(AccountStatus.WITHDRAWN);
        users.saveAndFlush(owner);
        assertThat(report(profileId, REPORTER, "SPAM", null)).isEqualTo(404);
    }

    @Test
    void unsupportedIconReferenceCannotCreateEvidenceFreeReport() throws Exception {
        Profile profile = profiles.findById(profileId).orElseThrow();
        profile.setIcon("https://example.invalid/avatar.png");
        profiles.saveAndFlush(profile);
        assertThat(report(profileId, REPORTER, "SPAM", null)).isEqualTo(500);
        assertThat(reports.count()).isZero();
    }

    @Test
    void invalidOtherAndMissingCsrfAreRejected() throws Exception {
        assertThat(report(profileId, REPORTER, "OTHER", "  ")).isEqualTo(400);
        mvc.perform(post("/api/profiles/{id}/reports", profileId).with(user(REPORTER))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"SPAM\"}"))
                .andExpect(status().isForbidden());
        assertThat(reports.count()).isZero();
    }

    @Test
    void anonymousCannotReport() throws Exception {
        mvc.perform(post("/api/profiles/{id}/reports", profileId).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"SPAM\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(reports.count()).isZero();
    }

    private int report(Long id, String email, String reason, String detail) throws Exception {
        String json = "{\"reason\":\"" + reason + "\"" +
                (detail == null ? "}" : ",\"detail\":\"" + detail + "\"}");
        return mvc.perform(post("/api/profiles/{id}/reports", id).with(user(email)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json))
                .andReturn().getResponse().getStatus();
    }

    private User account(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword("test-only");
        return user;
    }
}
