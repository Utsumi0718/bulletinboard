package com.example.bulletinboard.controller.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.bulletinboard.exception.handler.GlobalExceptionHandler;
import com.example.bulletinboard.model.AccountStatus;
import com.example.bulletinboard.model.Profile;
import com.example.bulletinboard.model.Report;
import com.example.bulletinboard.model.ReportProfileSnapshot;
import com.example.bulletinboard.model.ReportReason;
import com.example.bulletinboard.model.ReportTargetType;
import com.example.bulletinboard.model.TopicImage;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.AdminOperationLogRepository;
import com.example.bulletinboard.repository.ProfileRepository;
import com.example.bulletinboard.repository.ReportProfileSnapshotRepository;
import com.example.bulletinboard.repository.ReportRepository;
import com.example.bulletinboard.repository.TopicImageRepository;
import com.example.bulletinboard.repository.UserRepository;
import com.example.bulletinboard.security.SecurityConfig;
import com.example.bulletinboard.service.AdminProfileService;
import com.example.bulletinboard.service.CustomUserDetailsService;
import com.example.bulletinboard.service.TopicImageService;

/** E-4の管理API・DB・実Securityを専用H2で検証する。 */
@SpringBootTest(classes = AdminProfileFlowIntegrationTest.FlowConfiguration.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-profile-flow;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=false", "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminProfileFlowIntegrationTest {
    private static final String ADMIN = "admin@example.com";
    private static final String OWNER = "owner@example.com";

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = User.class)
    @EnableJpaRepositories(basePackageClasses = UserRepository.class)
    @Import({AdminProfileApiController.class, AdminProfileService.class,
            CustomUserDetailsService.class, SecurityConfig.class, GlobalExceptionHandler.class})
    static class FlowConfiguration { }

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ProfileRepository profiles;
    @Autowired TopicImageRepository images;
    @Autowired AdminOperationLogRepository logs;
    @Autowired ReportRepository reports;
    @Autowired ReportProfileSnapshotRepository snapshots;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach
    void setup() {
        logs.deleteAll();
        snapshots.deleteAll();
        reports.deleteAll();
        profiles.deleteAll();
        images.deleteAll();
        users.deleteAll();
        users.saveAndFlush(account("admin", ADMIN, "ROLE_ADMIN"));
        users.saveAndFlush(account("owner", OWNER, "ROLE_USER"));
        Profile profile = new Profile();
        profile.setUser(owner());
        profiles.saveAndFlush(profile);
    }

    @Test
    void detailUsesProfileIdAndShowsDefaultState() throws Exception {
        Profile profile = profile();
        assertThat(profile.getId()).isNotEqualTo(owner().getId());
        mvc.perform(get("/api/admin/profiles/{id}", profile.getId())
                .with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(profile.getId()))
                .andExpect(jsonPath("$.userId").value(owner().getId()))
                .andExpect(jsonPath("$.usesDefaultIcon").value(true))
                .andExpect(jsonPath("$.iconReference").doesNotExist())
                .andExpect(jsonPath("$.bio").doesNotExist());
    }

    @Test
    void userLookupUsesUserIdWithoutConfusingProfileId() throws Exception {
        mvc.perform(get("/api/admin/users/{id}/profile", owner().getId())
                .with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(profile().getId()))
                .andExpect(jsonPath("$.userId").value(owner().getId()));
        mvc.perform(get("/api/admin/profiles/{id}", owner().getId())
                .with(user(ADMIN).roles("ADMIN"))).andExpect(status().isNotFound());
    }

    @Test
    void detailShowsIconReferenceBioAndWithdrawnOwner() throws Exception {
        Profile profile = profile();
        profile.setIcon("https://example.invalid/avatar.png");
        profile.setBio("自己紹介");
        profiles.saveAndFlush(profile);
        User owner = owner();
        owner.setAccountStatus(AccountStatus.WITHDRAWN);
        users.saveAndFlush(owner);
        mvc.perform(get("/api/admin/profiles/{id}", profile.getId())
                .with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("WITHDRAWN"))
                .andExpect(jsonPath("$.iconReference").value("https://example.invalid/avatar.png"))
                .andExpect(jsonPath("$.usesDefaultIcon").value(false))
                .andExpect(jsonPath("$.bio").value("自己紹介"));
    }

    @ParameterizedTest
    @CsvSource({"/api/admin/profiles/999999", "/api/admin/users/999999/profile"})
    void missingProfileReturns404(String path) throws Exception {
        mvc.perform(get(path).with(user(ADMIN).roles("ADMIN"))).andExpect(status().isNotFound());
    }

    @Test
    void resetIconReturnsToDefaultAndLogsOnlySetState() throws Exception {
        Profile profile = profile();
        profile.setIcon("https://example.invalid/private-token/avatar.png");
        profiles.saveAndFlush(profile);
        mvc.perform(delete("/api/admin/profiles/{id}/icon", profile.getId())
                .with(user(ADMIN).roles("ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(profile().getIcon()).isNull();
        var history = logs.findByTargetTypeAndTargetIdOrderByCreatedAtAscIdAsc("PROFILE", profile.getId());
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().getAdminUser().getId())
                .isEqualTo(users.findByEmail(ADMIN).orElseThrow().getId());
        assertThat(history.getFirst().getAction()).isEqualTo("ICON_RESET");
        assertThat(history.getFirst().getBeforeStatus()).isEqualTo("SET");
        assertThat(history.getFirst().getAfterStatus()).isEqualTo("UNSET");
        assertThat(history.getFirst().getResult()).isEqualTo("SUCCESS");
        assertThat(jdbc.queryForMap("SELECT target_type, action, before_status, after_status, result "
                + "FROM admin_operation_logs WHERE target_id = ?", profile.getId()).toString())
                .doesNotContain("private-token");
    }

    @Test
    void clearBioRemovesTextAndLogsOnlySetState() throws Exception {
        Profile profile = profile();
        profile.setBio("private-email@example.invalid を含む自己紹介");
        profiles.saveAndFlush(profile);
        mvc.perform(delete("/api/admin/profiles/{id}/bio", profile.getId())
                .with(user(ADMIN).roles("ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(profile().getBio()).isNull();
        var history = logs.findByTargetTypeAndTargetIdOrderByCreatedAtAscIdAsc("PROFILE", profile.getId());
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().getAction()).isEqualTo("BIO_CLEAR");
        assertThat(history.getFirst().getBeforeStatus()).isEqualTo("SET");
        assertThat(history.getFirst().getAfterStatus()).isEqualTo("UNSET");
        assertThat(jdbc.queryForMap("SELECT target_type, action, before_status, after_status, result "
                + "FROM admin_operation_logs WHERE target_id = ?", profile.getId()).toString())
                .doesNotContain("private-email");
    }

    @ParameterizedTest
    @CsvSource({"icon", "bio"})
    void clearingAlreadyEmptyFieldDoesNotChangeTimestampOrWriteHistory(String field) throws Exception {
        LocalDateTime before = profile().getUpdatedAt();
        mvc.perform(delete("/api/admin/profiles/{id}/{field}", profile().getId(), field)
                .with(user(ADMIN).roles("ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(profile().getUpdatedAt()).isEqualTo(before);
        assertThat(logs.count()).isZero();
    }

    @Test
    void resettingSharedReferenceDoesNotDeleteImageOrOtherProfileReference() throws Exception {
        TopicImage image = images.saveAndFlush(new TopicImage(owner().getId(), "image/png", new byte[] {1, 2}));
        String reference = TopicImageService.URL_PREFIX + image.getId();
        Profile first = profile();
        first.setIcon(reference);
        profiles.saveAndFlush(first);
        User secondUser = users.saveAndFlush(account("second", "second@example.com", "ROLE_USER"));
        Profile second = new Profile();
        second.setUser(secondUser);
        second.setIcon(reference);
        second = profiles.saveAndFlush(second);
        mvc.perform(delete("/api/admin/profiles/{id}/icon", first.getId())
                .with(user(ADMIN).roles("ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(profiles.findById(first.getId()).orElseThrow().getIcon()).isNull();
        assertThat(profiles.findById(second.getId()).orElseThrow().getIcon()).isEqualTo(reference);
        assertThat(images.findById(image.getId())).isPresent();
    }

    @Test
    void clearingBioDoesNotRewriteExistingReportSnapshot() throws Exception {
        Profile profile = profile();
        profile.setBio("通報時の自己紹介");
        profiles.saveAndFlush(profile);
        User reporter = users.saveAndFlush(account("reporter", "reporter@example.com", "ROLE_USER"));
        Long[] reportId = new Long[1];
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            Profile managed = profiles.findByIdForReport(profile.getId()).orElseThrow();
            Report report = new Report();
            report.setReporterUser(reporter);
            report.setTargetType(ReportTargetType.PROFILE);
            report.setTargetId(managed.getId());
            report.setTargetOwnerUserId(owner().getId());
            report.setReason(ReportReason.SPAM);
            report = reports.saveAndFlush(report);
            snapshots.saveAndFlush(new ReportProfileSnapshot(report, managed));
            reportId[0] = report.getId();
        });
        mvc.perform(delete("/api/admin/profiles/{id}/bio", profile.getId())
                .with(user(ADMIN).roles("ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(profile().getBio()).isNull();
        assertThat(snapshots.findById(reportId[0]).orElseThrow().getBio()).isEqualTo("通報時の自己紹介");
    }

    @ParameterizedTest
    @CsvSource({"icon", "bio"})
    void missingProfileCannotBeChanged(String field) throws Exception {
        mvc.perform(delete("/api/admin/profiles/999999/{field}", field)
                .with(user(ADMIN).roles("ADMIN")).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(logs.count()).isZero();
    }

    @Test
    void anonymousAndOrdinaryUserCannotInspectOrChangeProfile() throws Exception {
        Long id = profile().getId();
        mvc.perform(get("/api/admin/profiles/{id}", id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/profiles/{id}", id).with(user(OWNER).roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/profiles/{id}/icon", id)
                .with(user(OWNER).roles("USER")).with(csrf())).andExpect(status().isForbidden());
        assertThat(logs.count()).isZero();
    }

    @Test
    void csrfAndInactiveAdminAreRejected() throws Exception {
        Profile profile = profile();
        profile.setBio("残す");
        profiles.saveAndFlush(profile);
        mvc.perform(delete("/api/admin/profiles/{id}/bio", profile.getId())
                .with(user(ADMIN).roles("ADMIN"))).andExpect(status().isForbidden());
        User admin = users.findByEmail(ADMIN).orElseThrow();
        admin.setAccountStatus(AccountStatus.FROZEN);
        users.saveAndFlush(admin);
        mvc.perform(delete("/api/admin/profiles/{id}/bio", profile.getId())
                .with(user(ADMIN).roles("ADMIN")).with(csrf())).andExpect(status().isForbidden());
        assertThat(profile().getBio()).isEqualTo("残す");
    }

    @Test
    void missingAdminRecordStopsChange() throws Exception {
        Profile profile = profile();
        profile.setBio("残す");
        profiles.saveAndFlush(profile);
        mvc.perform(delete("/api/admin/profiles/{id}/bio", profile.getId())
                .with(user("missing@example.com").roles("ADMIN")).with(csrf()))
                .andExpect(status().isInternalServerError());
        assertThat(profile().getBio()).isEqualTo("残す");
        assertThat(logs.count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"icon", "bio"})
    void historyFailureRollsBackProfileChange(String field) throws Exception {
        Profile profile = profile();
        profile.setIcon("https://example.invalid/icon.png");
        profile.setBio("残す");
        profiles.saveAndFlush(profile);
        jdbc.execute("ALTER TABLE admin_operation_logs ADD CONSTRAINT ck_profile_log_fail "
                + "CHECK (target_type <> 'PROFILE')");
        try {
            String response = mvc.perform(delete("/api/admin/profiles/{id}/{field}", profile.getId(), field)
                    .with(user(ADMIN).roles("ADMIN")).with(csrf()))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status").value(500))
                    .andReturn().getResponse().getContentAsString();
            assertThat(response).doesNotContain("icon.png", "残す", "CHECK", "SQL");
            assertThat(profile().getIcon()).isEqualTo("https://example.invalid/icon.png");
            assertThat(profile().getBio()).isEqualTo("残す");
            assertThat(logs.count()).isZero();
        } finally {
            jdbc.execute("ALTER TABLE admin_operation_logs DROP CONSTRAINT ck_profile_log_fail");
        }
    }

    private User account(String name, String email, String role) {
        User user = new User();
        user.setUsername(name);
        user.setEmail(email);
        user.setRole(role);
        user.setPassword("test-password");
        return user;
    }

    private User owner() { return users.findByEmail(OWNER).orElseThrow(); }

    private Profile profile() { return profiles.findByUserId(owner().getId()).orElseThrow(); }
}
