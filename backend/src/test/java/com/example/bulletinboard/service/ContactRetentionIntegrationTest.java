package com.example.bulletinboard.service;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.example.bulletinboard.dto.contact.ContactLegalHoldRequest;
import com.example.bulletinboard.model.*;
import com.example.bulletinboard.repository.*;
import com.example.bulletinboard.exception.ForbiddenOperationException;

/** 実Serviceのトランザクション終了後に専用H2を再読込する。ジョブの本番有効化は行わない。 */
@SpringBootTest(classes=ContactRetentionIntegrationTest.Configuration.class, properties={
        "spring.datasource.url=jdbc:h2:mem:contact-retention;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false", "spring.flyway.enabled=false", "spring.sql.init.mode=never", "contact.retention.enabled=false", "contact.retention.batch-size=2"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class ContactRetentionIntegrationTest {
    @org.springframework.boot.test.context.TestComponent
    @org.springframework.context.annotation.Configuration(proxyBeanMethods=false)
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    @org.springframework.boot.autoconfigure.domain.EntityScan(basePackageClasses=User.class)
    @org.springframework.data.jpa.repository.config.EnableJpaRepositories(basePackageClasses=UserRepository.class)
    @Import({ContactRetentionService.class,AdminContactService.class})
    static class Configuration { }
    @Autowired ContactRepository contacts;
    @Autowired ContactRetentionLogRepository retentionLogs;
    @Autowired AdminOperationLogRepository adminLogs;
    @Autowired UserRepository users;
    @Autowired ContactRetentionService retention;
    @Autowired AdminContactService adminService;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean Clock clock;
    private String adminEmail;
    private LocalDateTime now() {return LocalDateTime.now(ZoneId.of("Asia/Tokyo"));}
    @BeforeEach void setup() {
        org.mockito.Mockito.when(clock.instant()).thenAnswer(invocation -> Instant.now());
        contacts.deleteAll(); retentionLogs.deleteAll(); adminLogs.deleteAll();
        User admin=new User();admin.setUsername("admin-"+UUID.randomUUID());admin.setEmail("admin-"+UUID.randomUUID()+"@example.com");
        admin.setPassword("hash");admin.setRole("ROLE_ADMIN");adminEmail=users.saveAndFlush(admin).getEmail();
    }
    private Contact saved(ContactStatus status, LocalDateTime created) {
        Contact contact=new Contact();contact.setEmail("reply@example.com");contact.setCategory(ContactCategory.OTHER);
        contact.setSubject("相談");contact.setMessage("本文");contact.setStatus(status);contact.setCreatedAt(created);
        return contacts.saveAndFlush(contact);
    }
    @Test void keepsLessThanOneCalendarYear() {
        saved(ContactStatus.RESOLVED,now().minusYears(1).plusDays(1));
        assertThat(retention.deleteExpired()).isZero();assertThat(contacts.count()).isEqualTo(1);
    }
    @Test void deletesResolvedAtOneYearBoundaryAndKeepsContentOutOfLog() {
        Contact contact=saved(ContactStatus.RESOLVED,now().minusYears(1));
        assertThat(retention.deleteExpired()).isEqualTo(1);assertThat(contacts.existsById(contact.getId())).isFalse();
        var row=jdbc.queryForMap("SELECT contact_id,status,deleted_at FROM contact_retention_logs");
        assertThat(((Number)row.get("contact_id")).longValue()).isEqualTo(contact.getId());assertThat(row.get("status")).isEqualTo("RESOLVED");
        assertThat(jdbc.queryForList("SELECT * FROM contact_retention_logs").toString()).doesNotContain("reply@example.com","相談","本文");
    }
    @ParameterizedTest @ValueSource(strings={"UNANSWERED","IN_PROGRESS"})
    void keepsUnansweredAndInProgressEvenAfterOneYear(String status) {
        saved(ContactStatus.valueOf(status),now().minusYears(2));
        assertThat(retention.deleteExpired()).isZero();assertThat(contacts.count()).isEqualTo(1);
    }
    @Test void keepsFutureLegalHold() {
        Contact contact=saved(ContactStatus.RESOLVED,now().minusYears(2));
        adminService.updateLegalHold(contact.getId(),new ContactLegalHoldRequest(true,"保存が必要",now().plusDays(5)),adminEmail);
        assertThat(retention.deleteExpired()).isZero();
        assertThat(contacts.findById(contact.getId()).orElseThrow().getLegalHoldReason()).isEqualTo("保存が必要");
    }
    @Test void expiresLegalHoldAndDeletesOnNextRun() {
        Contact contact=saved(ContactStatus.RESOLVED,now().minusYears(2));
        jdbc.update("UPDATE contacts SET legal_hold_reason=?,legal_hold_until=? WHERE id=?","過去の保持",now().minusSeconds(1),contact.getId());
        assertThat(retention.deleteExpired()).isEqualTo(1);
    }
    @Test void manualReleaseEnablesNextRunAndRecordsBothOperations() {
        Contact contact=saved(ContactStatus.RESOLVED,now().minusYears(2));
        adminService.updateLegalHold(contact.getId(),new ContactLegalHoldRequest(true,"保存が必要",now().plusDays(5)),adminEmail);
        adminService.updateLegalHold(contact.getId(),new ContactLegalHoldRequest(false,null,null),adminEmail);
        assertThat(retention.deleteExpired()).isEqualTo(1);
        assertThat(adminLogs.findAll()).extracting(AdminOperationLog::getAction).containsExactlyInAnyOrder("LEGAL_HOLD_SET","LEGAL_HOLD_RELEASE");
        assertThat(retentionLogs.count()).isEqualTo(1);
    }
    @Test void manualDeleteIsRejectedDuringHold() {
        Contact contact=saved(ContactStatus.RESOLVED,now().minusYears(2));
        adminService.updateLegalHold(contact.getId(),new ContactLegalHoldRequest(true,"保持",now().plusDays(1)),adminEmail);
        assertThatThrownBy(()->adminService.deleteContact(contact.getId(),adminEmail)).isInstanceOf(ForbiddenOperationException.class);
        assertThat(contacts.existsById(contact.getId())).isTrue();assertThat(adminLogs.count()).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"missingReason","blankReason","missingUntil","pastUntil"})
    void invalidLegalHoldCannotChangeRecord(String input) {
        Contact contact=saved(ContactStatus.RESOLVED,now().minusYears(2));
        String reason=input.equals("missingReason")?null:input.equals("blankReason")?"  ":"保持";
        var until=input.equals("missingUntil")?null:input.equals("pastUntil")?now().minusDays(1):now().plusDays(1);
        assertThatThrownBy(()->adminService.updateLegalHold(contact.getId(),new ContactLegalHoldRequest(true,reason,until),adminEmail)).isInstanceOf(IllegalArgumentException.class);
        assertThat(adminLogs.count()).isZero(); assertThat(contacts.findById(contact.getId()).orElseThrow().getLegalHoldUntil()).isNull();
    }
    @Test void leavesExistingAdminHistoryAfterAutomaticDeletion() {
        Contact contact=saved(ContactStatus.UNANSWERED,now().minusYears(2));
        adminService.updateStatus(contact.getId(),ContactStatus.RESOLVED,adminEmail);
        assertThat(retention.deleteExpired()).isEqualTo(1);assertThat(adminLogs.count()).isEqualTo(1);
        assertThat(retentionLogs.count()).isEqualTo(1);
        assertThat(retention.deleteExpired()).isZero();
    }
    @Test void logFailureRollsBackDeletion() {
        Contact contact=saved(ContactStatus.RESOLVED,now().minusYears(2));
        jdbc.execute("ALTER TABLE contact_retention_logs ADD CONSTRAINT reject_target CHECK(contact_id <> "+contact.getId()+")");
        try {
            assertThatThrownBy(retention::deleteExpired).isInstanceOf(RuntimeException.class);
            assertThat(contacts.existsById(contact.getId())).isTrue(); assertThat(retentionLogs.count()).isZero();
        } finally {jdbc.execute("ALTER TABLE contact_retention_logs DROP CONSTRAINT reject_target");}
    }
    @Test void deleteFailureRollsBackAlreadyFlushedLog() {
        Contact contact=saved(ContactStatus.RESOLVED,now().minusYears(2));
        jdbc.execute("CREATE TABLE contact_test_blocker(contact_id BIGINT REFERENCES contacts(id))");
        jdbc.update("INSERT INTO contact_test_blocker VALUES(?)",contact.getId());
        try {
            assertThatThrownBy(retention::deleteExpired).isInstanceOf(RuntimeException.class);
            assertThat(contacts.existsById(contact.getId())).isTrue(); assertThat(retentionLogs.count()).isZero();
        } finally {jdbc.execute("DROP TABLE contact_test_blocker");}
    }
    @Test void processesAllExpiredRowsAcrossBatches() {
        for(int i=0;i<3;i++) saved(ContactStatus.RESOLVED,now().minusYears(2));
        assertThat(retention.deleteExpired()).isEqualTo(3);assertThat(contacts.count()).isZero();assertThat(retentionLogs.count()).isEqualTo(3);
    }
    @Test void leapDayExpiresOnNextCalendarYearsFebruary28() {
        org.mockito.Mockito.when(clock.instant()).thenReturn(LocalDateTime.of(2025,2,28,12,0).atZone(ZoneId.of("Asia/Tokyo")).toInstant());
        saved(ContactStatus.RESOLVED,LocalDateTime.of(2024,2,29,12,0));
        saved(ContactStatus.RESOLVED,LocalDateTime.of(2024,2,29,12,1));
        assertThat(retention.deleteExpired()).isEqualTo(1);assertThat(contacts.count()).isEqualTo(1);
    }
}
