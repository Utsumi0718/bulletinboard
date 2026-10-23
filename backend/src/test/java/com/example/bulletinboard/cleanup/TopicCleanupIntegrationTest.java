package com.example.bulletinboard.cleanup;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import com.example.bulletinboard.controller.api.AdminTopicCleanupApiController;
import com.example.bulletinboard.exception.handler.GlobalExceptionHandler;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.UserRepository;
import com.example.bulletinboard.repository.WinnerAchievementRepository;
import com.example.bulletinboard.security.SecurityConfig;
import com.example.bulletinboard.service.CustomUserDetailsService;
import com.example.bulletinboard.service.AdminReportService;
import com.example.bulletinboard.service.AnswerService;
import com.example.bulletinboard.service.LikeService;
import com.example.bulletinboard.service.TopicService;
import com.example.bulletinboard.service.TopicImageService;
import com.example.bulletinboard.repository.TopicRepository;
import com.example.bulletinboard.model.ReportStatus;
import com.example.bulletinboard.model.Topic;

/** Dedicated disposable H2; runs real Flyway scripts. H2 does not verify MySQL lock/driver behavior. */
@SpringBootTest(classes=TopicCleanupIntegrationTest.Config.class,properties={
    "spring.datasource.url=jdbc:h2:mem:topic-cleanup;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=none",
    "spring.flyway.enabled=true","spring.sql.init.mode=never","spring.jpa.open-in-view=false",
    "spring.jpa.defer-datasource-initialization=false","debug=false","logging.level.root=WARN","logging.level.org.springframework=WARN",
    "topic.cleanup.enabled=false","contact.retention.enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class TopicCleanupIntegrationTest {
    @Configuration(proxyBeanMethods=false) @EnableAutoConfiguration
    @EntityScan(basePackageClasses=User.class) @EnableJpaRepositories(basePackageClasses=UserRepository.class)
    @Import({CleanupProperties.class,CleanupDatabase.class,CleanupLease.class,TopicCleanupService.class,
        CleanupAdminService.class,AdminTopicCleanupApiController.class,SecurityConfig.class,
        CustomUserDetailsService.class,GlobalExceptionHandler.class,AdminReportService.class,
        AnswerService.class,LikeService.class,TopicService.class,TopicImageService.class})
    static class Config { }
    @Autowired JdbcTemplate jdbc;
    @Autowired CleanupDatabase db;
    @Autowired CleanupLease lease;
    @Autowired CleanupProperties properties;
    @Autowired TopicCleanupService cleanup;
    @Autowired CleanupAdminService admin;
    @Autowired WinnerAchievementRepository achievements;
    @Autowired MockMvc mvc;
    @Autowired AdminReportService reportAdmin;
    @Autowired AnswerService answerService;
    @Autowired TopicService topicService;
    @Autowired UserRepository userRepository;
    long owner,other,administrator;
    static final String ADMIN="cleanup-admin@example.invalid";

    @BeforeEach void reset() {
        for(String table:List.of("topic_cleanup_attempts","topic_cleanup_runs","topic_cleanup_targets","ranking_result_archives","winner_achievement_archives",
                "admin_operation_logs","report_topic_snapshots","report_answer_snapshots","report_profile_snapshots","reports",
                "x_post_history","winner_achievements","ranking_results","ranking_judgments","likes","answers","topics","topic_images","profiles","profile_images","password_reset_tokens","users")) jdbc.update("DELETE FROM "+table);
        jdbc.update("UPDATE cleanup_job_leases SET owner_id=NULL,generation=0,expires_at='1970-01-01 00:00:00'");
        properties.setEnabled(false); properties.setDailyLimit(100);
        properties.setLeaseSeconds(300);properties.setTransactionTimeoutSeconds(30);properties.setHeartbeatSeconds(60);properties.setLockWaitSeconds(5);
        owner=account("owner","owner@example.invalid","ROLE_USER");
        other=account("other","other@example.invalid","ROLE_USER");
        administrator=account("admin",ADMIN,"ROLE_ADMIN");
    }
    long account(String name,String email,String role) {
        jdbc.update("INSERT INTO users(username,email,password,role,created_at) VALUES (?,?, 'not-a-real-credential',?,?)",name,email,role,CleanupDatabase.stamp(db.now()));
        return jdbc.queryForObject("SELECT id FROM users WHERE email=?",Long.class,email);
    }
    long topic(long hours,boolean verified) {
        String image=image(owner);
        jdbc.update("INSERT INTO topics(user_id,title,image,question,created_at,updated_at,deleted_at,cleanup_deleted_at_utc) VALUES (?,'title',?,'question',?,?,?,?)",
            owner,"/api/topic-images/"+image,CleanupDatabase.stamp(db.now()),CleanupDatabase.stamp(db.now()),
            CleanupDatabase.stamp(db.now().minusHours(hours)),verified?CleanupDatabase.stamp(db.now().minusHours(hours)):null);
        return jdbc.queryForObject("SELECT MAX(id) FROM topics",Long.class);
    }
    String image(long user) {
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO topic_images(id,owner_user_id,content_type,byte_size,content,created_at) VALUES (?,?,'image/png',3,?,?)",
            id,user,new byte[]{1,2,3},CleanupDatabase.stamp(db.now()));return id;
    }
    long answer(long topic,long user) {
        jdbc.update("INSERT INTO answers(topic_id,user_id,content,created_at,updated_at) VALUES (?,?,'answer',?,?)",topic,user,CleanupDatabase.stamp(db.now()),CleanupDatabase.stamp(db.now()));
        return jdbc.queryForObject("SELECT MAX(id) FROM answers",Long.class);
    }
    long report(long id,String type,String status) {
        jdbc.update("INSERT INTO reports(reporter_user_id,target_type,target_id,reason,status,created_at,updated_at) VALUES (?,?,?,'SPAM',?,?,?)",
            other,type,id,status,CleanupDatabase.stamp(db.now()),CleanupDatabase.stamp(db.now()));
        return jdbc.queryForObject("SELECT MAX(id) FROM reports",Long.class);
    }
    long judgment(long topic,String checkpoint) {
        jdbc.update("INSERT INTO ranking_judgments(topic_id,checkpoint,judged_at) VALUES (?,?,?)",topic,checkpoint,CleanupDatabase.stamp(db.now().minusDays(10)));
        return jdbc.queryForObject("SELECT MAX(id) FROM ranking_judgments",Long.class);
    }
    long execute(long topic) {
        var token=lease.acquire().orElseThrow();
        try {
            long run=cleanup.createRun(token,day());
            var c=cleanup.candidates(100,day()).stream().filter(x->x.topicId()==topic).findFirst().orElseThrow();
            long attempt=cleanup.reserve(token,run,c);cleanup.process(token,attempt);cleanup.finishRun(token,run,"FINISHED");return attempt;
        } finally {lease.release(token);}
    }
    LocalDate day(){return db.now().plusHours(9).toLocalDate();}
    long count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}
    String state(long topic){return jdbc.queryForObject("SELECT state FROM topic_cleanup_targets WHERE original_topic_id=?",String.class,topic);}
    String outcome(long attempt){return jdbc.queryForObject("SELECT outcome FROM topic_cleanup_attempts WHERE id=?",String.class,attempt);}

    @Test void archivesAllTiesAndPreservesInitialAchievementAndEvidence() {
        long t=topic(721,true),a=answer(t,owner),b=answer(t,other),j=judgment(t,"DAY_7");
        jdbc.update("INSERT INTO ranking_results(answer_id,ranking_judgment_id,like_count) VALUES (?,?,9),(?,?,9)",a,j,b,j);
        jdbc.update("INSERT INTO winner_achievements(user_id,topic_id,answer_id,like_count,achieved_at) VALUES (?,?,?,9,?)",owner,t,a,CleanupDatabase.stamp(db.now().minusDays(10)));
        jdbc.update("INSERT INTO likes(user_id,answer_id,created_at) VALUES (?,?,?)",other,a,CleanupDatabase.stamp(db.now()));
        long r=report(t,"TOPIC","RESOLVED");
        jdbc.update("INSERT INTO report_topic_snapshots(report_id,title,question,content_type,byte_size,image_content) VALUES (?,'evidence','evidence','image/png',3,?)",r,new byte[]{4,5,6});
        assertThat(outcome(execute(t))).isEqualTo("COMPLETED");
        assertThat(count("topics")).isZero();assertThat(count("answers")).isZero();assertThat(count("likes")).isZero();
        assertThat(count("ranking_results")).isZero();assertThat(count("ranking_judgments")).isZero();
        assertThat(count("ranking_result_archives")).isEqualTo(2);assertThat(count("winner_achievement_archives")).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT like_count FROM ranking_result_archives",Integer.class)).containsOnly(9);
        assertThat(achievements.countIncludingArchives(owner)).isEqualTo(1);
        assertThat(count("reports")).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT image_content FROM report_topic_snapshots",byte[].class)).containsExactly(4,5,6);
    }
    @Test void noWinnerJudgmentIsRemovedWithoutCreatingArchive() {long t=topic(721,true);judgment(t,"DAY_7");execute(t);assertThat(count("ranking_judgments")).isZero();assertThat(count("ranking_result_archives")).isZero();}
    @Test void matchingExistingArchiveDoesNotDuplicateAchievement() {
        long t=topic(721,true),a=answer(t,owner);var at=CleanupDatabase.stamp(db.now().minusDays(5));
        jdbc.update("INSERT INTO winner_achievements(user_id,topic_id,answer_id,like_count,achieved_at) VALUES (?,?,?,7,?)",owner,t,a,at);
        jdbc.update("INSERT INTO winner_achievement_archives(user_id,original_topic_id,original_answer_id,like_count,achieved_at,archived_at) VALUES (?,?,?,7,?,?)",owner,t,a,at,at);
        assertThat(achievements.countIncludingArchives(owner)).isEqualTo(1);execute(t);assertThat(count("winner_achievement_archives")).isEqualTo(1);
    }
    @Test void archiveMismatchStopsAndRollsBackEveryDeletion() {
        long t=topic(721,true),a=answer(t,owner),j=judgment(t,"DAY_7");
        jdbc.update("INSERT INTO ranking_results(answer_id,ranking_judgment_id,like_count) VALUES (?,?,3)",a,j);
        jdbc.update("INSERT INTO ranking_result_archives(user_id,original_topic_id,original_answer_id,checkpoint,like_count,judged_at,archived_at) VALUES (?,?,?,'DAY_7',99,?,?)",owner,t,a,CleanupDatabase.stamp(db.now()),CleanupDatabase.stamp(db.now()));
        assertThat(outcome(execute(t))).isEqualTo("FAILED");assertThat(state(t)).isEqualTo("STOPPED");assertThat(count("topics")).isEqualTo(1);assertThat(count("ranking_results")).isEqualTo(1);
    }
    @Test void unexpectedForeignKeyRollsBackArchiveAndStops() {
        long t=topic(721,true),a=answer(t,owner),j=judgment(t,"DAY_7");
        jdbc.update("INSERT INTO ranking_results(answer_id,ranking_judgment_id,like_count) VALUES (?,?,3)",a,j);
        jdbc.execute("CREATE TABLE cleanup_test_blocker(id BIGINT PRIMARY KEY,topic_id BIGINT REFERENCES topics(id))");
        try {jdbc.update("INSERT INTO cleanup_test_blocker VALUES (1,?)",t);execute(t);
            assertThat(state(t)).isEqualTo("STOPPED");assertThat(count("ranking_result_archives")).isZero();assertThat(count("answers")).isEqualTo(1);
        } finally {jdbc.execute("DROP TABLE cleanup_test_blocker");}
    }
    @Test void unverifiedLegacyTimeIsHeld() {long t=topic(1000,false);assertThat(outcome(execute(t))).isEqualTo("HELD_TIME_UNVERIFIED");assertThat(count("topics")).isEqualTo(1);}
    @Test void newLogicalDeletionRecordsExplicitUtcWithoutDeletingAnswers() {
        long t=topic(0,false),a=answer(t,owner);jdbc.update("UPDATE topics SET deleted_at=NULL WHERE id=?",t);
        topicService.deleteById(t,"owner@example.invalid",false);
        var recorded=db.jdbc.queryForObject("SELECT cleanup_deleted_at_utc FROM topics WHERE id=?",
                (rs,n)->rs.getObject(1,java.time.LocalDateTime.class),t);
        assertThat(recorded).isCloseTo(db.now(),within(5,java.time.temporal.ChronoUnit.SECONDS));
        assertThat(jdbc.queryForObject("SELECT deleted_at FROM answers WHERE id=?",Timestamp.class,a)).isNull();
        assertThat(cleanup.candidates(100,day())).isEmpty();
    }
    @ParameterizedTest @CsvSource({"719,false","720,true"})
    void exactly720HoursDefinesCandidateBoundary(long hours,boolean expected) {long t=topic(hours,true);assertThat(cleanup.candidates(100,day()).stream().anyMatch(c->c.topicId()==t)).isEqualTo(expected);}
    @Test void publicTopicIsNotCandidate() {long t=topic(1000,true);jdbc.update("UPDATE topics SET deleted_at=NULL WHERE id=?",t);assertThat(cleanup.candidates(100,day())).isEmpty();}
    @ParameterizedTest @CsvSource({"TOPIC,UNHANDLED","TOPIC,IN_PROGRESS","ANSWER,UNHANDLED","ANSWER,IN_PROGRESS"})
    void unresolvedReportsHoldWholeTopicIncludingDeletedAnswers(String type,String status) {
        long t=topic(721,true),a=answer(t,owner);jdbc.update("UPDATE answers SET deleted_at=? WHERE id=?",CleanupDatabase.stamp(db.now()),a);
        report(type.equals("TOPIC")?t:a,type,status);execute(t);assertThat(state(t)).isEqualTo("HELD_REPORT");
        assertThat(jdbc.queryForObject("SELECT failure_count FROM topic_cleanup_targets",Integer.class)).isZero();
    }
    @Test void resolvedReportDoesNotHold() {long t=topic(721,true);report(t,"TOPIC","RESOLVED");execute(t);assertThat(state(t)).isEqualTo("COMPLETED");}
    @Test void profileReportWithSameNumericIdDoesNotHoldTopic() {long t=topic(721,true);report(t,"PROFILE","UNHANDLED");execute(t);assertThat(state(t)).isEqualTo("COMPLETED");}
    @Test void xReferenceIsHeldWithoutDeletingHistory() {
        long t=topic(721,true),a=answer(t,owner);jdbc.update("INSERT INTO x_post_history(user_id,topic_id,answer_id,created_at,updated_at) VALUES (?,?,?,?,?)",owner,t,a,CleanupDatabase.stamp(db.now()),CleanupDatabase.stamp(db.now()));
        execute(t);assertThat(state(t)).isEqualTo("HELD_X_REFERENCE");assertThat(count("x_post_history")).isEqualTo(1);
    }
    @Test void sharedOriginalIsRetainedEvenWhenOtherTopicIsLogicallyDeleted() {
        long t=topic(721,true),otherTopic=topic(1,true);String url=jdbc.queryForObject("SELECT image FROM topics WHERE id=?",String.class,t);
        jdbc.update("UPDATE topics SET image=? WHERE id=?",url,otherTopic);execute(t);assertThat(state(t)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT pending_image_id FROM topic_cleanup_targets WHERE original_topic_id=?",String.class,t)).isEqualTo(url.substring(18));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM topic_images WHERE id=?",Long.class,url.substring(18))).isEqualTo(1);
    }
    @Test void unsharedAssociatedOriginalIsRemoved() {long t=topic(721,true);execute(t);assertThat(count("topic_images")).isZero();}
    @Test void neverUsedUploadIsOutsideCleanupScope() {long t=topic(721,true);String unused=image(owner);execute(t);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM topic_images WHERE id=?",Long.class,unused)).isEqualTo(1);}
    @Test void unownedLegacyOriginalIsNeverRemoved() {long t=topic(721,true);String unowned=image(other);jdbc.update("UPDATE topics SET image=? WHERE id=?","/api/topic-images/"+unowned,t);execute(t);assertThat(jdbc.queryForObject("SELECT pending_image_id FROM topic_cleanup_targets",String.class)).isEqualTo(unowned);assertThat(count("topic_images")).isEqualTo(2);}
    @Test void dryRunDoesNotWriteAnyMetadataOrDelete() {topic(721,true);assertThat(cleanup.dryRun(100)).hasSize(1);for(String t:List.of("topic_cleanup_runs","topic_cleanup_attempts","topic_cleanup_targets","ranking_result_archives"))assertThat(count(t)).isZero();assertThat(count("topics")).isEqualTo(1);}
    @Test void sameDayRunCannotRestartAfterLeaseRelease() {var t=lease.acquire().orElseThrow();cleanup.createRun(t,day());lease.release(t);var next=lease.acquire().orElseThrow();assertThat(cleanup.createRun(next,day())).isNull();}
    @Test void dailyLimitIncludesReservationsAndSameTopicCannotConsumeTwoSlots() {
        properties.setDailyLimit(1);topic(721,true);topic(721,true);var t=lease.acquire().orElseThrow();long run=cleanup.createRun(t,day());var candidates=cleanup.candidates(100,day());
        assertThat(cleanup.reserve(t,run,candidates.getFirst())).isNotNull();assertThat(cleanup.reserve(t,run,candidates.getFirst())).isNull();assertThat(cleanup.reserve(t,run,candidates.getLast())).isNull();assertThat(count("topic_cleanup_attempts")).isEqualTo(1);
    }
    @Test void oldHeldTargetsDoNotStarveUncheckedCandidates() {
        long t=topic(1000,false);execute(t);long newer=topic(721,true);assertThat(cleanup.candidates(1,day()).getFirst().topicId()).isEqualTo(newer);
    }
    @Test void resumeIsAuditedAndOnlyResetsCurrentCycle() {
        long t=topic(721,true);execute(t);jdbc.update("UPDATE topic_cleanup_targets SET state='STOPPED',failure_count=4 WHERE original_topic_id=?",t);
        long attempts=count("topic_cleanup_attempts");admin.resume(ADMIN,t,"原因を修正済み");assertThat(state(t)).isEqualTo("RETRY_WAIT");
        assertThat(count("topics")).isZero();assertThat(count("topic_cleanup_attempts")).isEqualTo(attempts);
        assertThat(jdbc.queryForObject("SELECT failure_count FROM topic_cleanup_targets",Integer.class)).isZero();assertThat(jdbc.queryForObject("SELECT reason FROM admin_operation_logs",String.class)).isEqualTo("原因を修正済み");
    }
    @Test void failedAuditRollsBackResume() {
        long t=topic(721,true);execute(t);jdbc.update("UPDATE topic_cleanup_targets SET state='STOPPED',failure_count=4 WHERE original_topic_id=?",t);
        jdbc.execute("ALTER TABLE admin_operation_logs ADD CONSTRAINT cleanup_audit_fail CHECK(action<>'RESUME')");
        try{assertThatThrownBy(()->admin.resume(ADMIN,t,"確認済み")).isInstanceOf(RuntimeException.class);assertThat(state(t)).isEqualTo("STOPPED");}
        finally{jdbc.execute("ALTER TABLE admin_operation_logs DROP CONSTRAINT cleanup_audit_fail");}
    }
    @Test void previousReservedButNotStartedAttemptDoesNotConsumeFailure() {
        long topic=topic(721,true);var t=lease.acquire().orElseThrow();long run=cleanup.createRun(t,day().minusDays(1));long a=cleanup.reserve(t,run,cleanup.candidates(100,day()).getFirst());
        cleanup.reconcile(t,day());assertThat(outcome(a)).isEqualTo("NOT_STARTED");assertThat(jdbc.queryForObject("SELECT failure_count FROM topic_cleanup_targets",Integer.class)).isZero();assertThat(count("topics")).isEqualTo(1);
    }
    @Test void interruptedStartedAttemptIsCountedOnceAcrossRepeatedReconciliation() {
        long topic=topic(721,true);var t=lease.acquire().orElseThrow();long run=cleanup.createRun(t,day().minusDays(1));long a=cleanup.reserve(t,run,cleanup.candidates(100,day()).getFirst());
        jdbc.update("UPDATE topic_cleanup_attempts SET outcome='STARTED',started_at=? WHERE id=?",CleanupDatabase.stamp(db.now()),a);
        cleanup.reconcile(t,day());cleanup.reconcile(t,day());assertThat(state(topic)).isEqualTo("RETRY_WAIT");assertThat(jdbc.queryForObject("SELECT failure_count FROM topic_cleanup_targets",Integer.class)).isEqualTo(1);
    }
    @Test void fourthTransientFailureStopsAndRetainsAttemptHistory() {
        long topic=topic(721,true);var t=lease.acquire().orElseThrow();
        for(int i=4;i>0;i--){long run=cleanup.createRun(t,day().minusDays(i));long a=cleanup.reserve(t,run,cleanup.candidates(100,day()).getFirst());
            jdbc.update("UPDATE topic_cleanup_attempts SET outcome='STARTED',started_at=? WHERE id=?",CleanupDatabase.stamp(db.now()),a);cleanup.reconcile(t,day());}
        assertThat(state(topic)).isEqualTo("STOPPED");assertThat(count("topic_cleanup_attempts")).isEqualTo(4);
    }
    @Test void missingTopicWithoutSuccessRecordStopsInsteadOfAssumingSuccess() {
        long topic=topic(721,true);var t=lease.acquire().orElseThrow();long run=cleanup.createRun(t,day().minusDays(1));long a=cleanup.reserve(t,run,cleanup.candidates(100,day()).getFirst());
        jdbc.update("DELETE FROM topics WHERE id=?",topic);cleanup.reconcile(t,day());assertThat(state(topic)).isEqualTo("STOPPED");assertThat(outcome(a)).isEqualTo("FAILED");
    }
    @Test void disabledSchedulerDoesNothing() {new TopicCleanupScheduler(lease,db,properties,cleanup).run();assertThat(count("topic_cleanup_runs")).isZero();}

    @Test void deletedParentRejectsAnswerEditingAndNewAnswers() {
        long t=topic(721,true),a=answer(t,owner);
        assertThatThrownBy(()->answerService.updateAnswer(a,"owner@example.invalid","changed"))
                .isInstanceOf(com.example.bulletinboard.exception.TopicNotFoundException.class);
        var newAnswer=new com.example.bulletinboard.model.Answer();var parent=new Topic();parent.setId(t);newAnswer.setTopic(parent);
        assertThatThrownBy(()->answerService.saveAnswer(newAnswer)).isInstanceOf(com.example.bulletinboard.exception.TopicNotFoundException.class);
        assertThat(jdbc.queryForObject("SELECT content FROM answers WHERE id=?",String.class,a)).isEqualTo("answer");
    }
    @Test void reportReopeningWaitsForTheSameTopicLockThenPreventsDeletion()throws Exception {
        long t=topic(721,true),r=report(t,"TOPIC","RESOLVED");
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var holding=executor.submit(()->db.transaction(()->{db.jdbc.queryForList("SELECT id FROM topics WHERE id=? FOR UPDATE",t);locked.countDown();
                try{if(!release.await(3,TimeUnit.SECONDS))throw new IllegalStateException();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}return null;}));
            assertThat(locked.await(2,TimeUnit.SECONDS)).isTrue();
            var reopening=executor.submit(()->reportAdmin.updateStatus(r,ReportStatus.UNHANDLED,ADMIN));
            try{assertThatThrownBy(()->reopening.get(100,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);}finally{release.countDown();}
            holding.get(3,TimeUnit.SECONDS);reopening.get(3,TimeUnit.SECONDS);
        }finally{release.countDown();}
        execute(t);assertThat(state(t)).isEqualTo("HELD_REPORT");
    }
    @Test void imageAttachmentWaitsAndCannotUseAnOriginalDeletedByCleanup()throws Exception {
        long t=topic(721,true);String url=jdbc.queryForObject("SELECT image FROM topics WHERE id=?",String.class,t);
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var holding=executor.submit(()->db.transaction(()->{db.jdbc.queryForList("SELECT id FROM topics WHERE id=? FOR UPDATE",t);
                db.jdbc.queryForList("SELECT id FROM topic_images WHERE id=? FOR UPDATE",url.substring(18));locked.countDown();
                try{if(!release.await(3,TimeUnit.SECONDS))throw new IllegalStateException();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
                db.jdbc.update("DELETE FROM topics WHERE id=?",t);db.jdbc.update("DELETE FROM topic_images WHERE id=?",url.substring(18));return null;}));
            assertThat(locked.await(2,TimeUnit.SECONDS)).isTrue();
            var attaching=executor.submit(()->{var created=new Topic();created.setUser(userRepository.findById(owner).orElseThrow());created.setImage(url);created.setTitle("new");created.setQuestion("new");return topicService.save(created);});
            try{assertThatThrownBy(()->attaching.get(100,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);}finally{release.countDown();}
            holding.get(3,TimeUnit.SECONDS);
            assertThatThrownBy(()->attaching.get(3,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(com.example.bulletinboard.exception.TopicImageException.class);
        }finally{release.countDown();}
        assertThat(count("topics")).isZero();
    }
    @Test void originalCanBeRecheckedAfterTopicDeletionWithoutLosingSuccessMarker() {
        long first=topic(721,true),second=topic(721,true);String url=jdbc.queryForObject("SELECT image FROM topics WHERE id=?",String.class,first);
        jdbc.update("UPDATE topics SET image=? WHERE id=?",url,second);execute(first);
        jdbc.update("UPDATE topic_cleanup_runs SET scheduled_date=?",java.sql.Date.valueOf(day().minusDays(2)));
        execute(second);jdbc.update("UPDATE topic_cleanup_runs SET scheduled_date=? WHERE scheduled_date=?",java.sql.Date.valueOf(day().minusDays(1)),java.sql.Date.valueOf(day()));
        long attempt=execute(first);assertThat(outcome(attempt)).isEqualTo("IMAGE_COMPLETED");assertThat(state(first)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT pending_image_id FROM topic_cleanup_targets WHERE original_topic_id=?",String.class,first)).isNull();
    }
    @Test void databaseErrorHasFixedResponseAndOneSafeLogWithoutThrowable()throws Exception {
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(AdminTopicCleanupApiController.class);
        var appender=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();appender.start();logger.addAppender(appender);
        jdbc.execute("ALTER TABLE topic_cleanup_runs RENAME TO cleanup_test_runs_backup");
        try {
            mvc.perform(get("/api/admin/topic-cleanup/runs").with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("処理を完了できませんでした。"))
                .andExpect(jsonPath("$.path").value("/api/admin/topic-cleanup/runs"));
            assertThat(appender.list).hasSize(1);assertThat(appender.list.getFirst().getThrowableProxy()).isNull();
            assertThat(appender.list.getFirst().getFormattedMessage()).doesNotContain("SELECT",ADMIN,"cleanup_test_runs_backup");
        } finally {jdbc.execute("ALTER TABLE cleanup_test_runs_backup RENAME TO topic_cleanup_runs");logger.detachAppender(appender);appender.stop();}
    }
    @Test void resumeRejectsACompletedTarget()throws Exception {
        long t=topic(721,true);execute(t);
        mvc.perform(post("/api/admin/topic-cleanup/targets/"+t+"/resume").with(user(ADMIN).roles("ADMIN")).with(csrf())
                .contentType("application/json").content("{\"reason\":\"確認済み\"}")).andExpect(status().isConflict());
        assertThat(count("admin_operation_logs")).isZero();
    }

    // Lease tests use separate transactions. They establish H2 behavior only.
    @Test void activeLeaseCannotBeAcquiredTwice(){lease.acquire().orElseThrow();assertThat(lease.acquire()).isEmpty();}
    @Test void expiredLeaseChangesGeneration(){var old=lease.acquire().orElseThrow();expire();var next=lease.acquire().orElseThrow();assertThat(next.generation()).isGreaterThan(old.generation());assertThat(next.owner()).isNotEqualTo(old.owner());}
    @Test void oldGenerationCannotDeleteAfterTakeover(){var old=lease.acquire().orElseThrow();expire();lease.acquire().orElseThrow();assertThatThrownBy(()->db.transaction(()->{lease.verify(old,true);return null;})).isInstanceOf(CleanupLease.Lost.class);}
    @Test void expiredOwnerCannotRenewItself(){var old=lease.acquire().orElseThrow();expire();assertThat(lease.renew(old)).isFalse();}
    @Test void wrongOwnerCannotRenew(){var t=lease.acquire().orElseThrow();assertThat(lease.renew(new CleanupLease.Token("other",t.generation()))).isFalse();}
    @Test void staleReleaseDoesNotReleaseNewOwner(){var old=lease.acquire().orElseThrow();expire();var next=lease.acquire().orElseThrow();lease.release(old);assertThat(lease.renew(next)).isTrue();}
    @Test void releaseAllowsNextGeneration(){var old=lease.acquire().orElseThrow();lease.release(old);assertThat(lease.acquire().orElseThrow().generation()).isEqualTo(old.generation()+1);}
    @Test void insufficientRemainingLeasePreventsNewTopic(){var t=lease.acquire().orElseThrow();jdbc.update("UPDATE cleanup_job_leases SET expires_at=?",CleanupDatabase.stamp(db.now().plusSeconds(10)));assertThatThrownBy(()->db.transaction(()->{lease.verify(t,true);return null;})).isInstanceOf(CleanupLease.Lost.class);}
    @Test void finalExpiryCheckRejectsCommit(){var t=lease.acquire().orElseThrow();assertThatThrownBy(()->db.transaction(()->{lease.verify(t,true);jdbc.update("UPDATE cleanup_job_leases SET expires_at='1970-01-01 00:00:00'");lease.verify(t,false);return null;})).isInstanceOf(CleanupLease.Lost.class);assertThat(lease.renew(t)).isTrue();}
    @Test void heartbeatWaitsForDeletionLeaseRowLock() throws Exception {
        var t=lease.acquire().orElseThrow();var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var deletion=executor.submit(()->db.transaction(()->{lease.verify(t,true);locked.countDown();try{if(!release.await(3,TimeUnit.SECONDS))throw new IllegalStateException();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}return null;}));
            assertThat(locked.await(2,TimeUnit.SECONDS)).isTrue();var heartbeat=executor.submit(()->lease.renew(t));
            try{assertThatThrownBy(()->heartbeat.get(100,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);}finally{release.countDown();}
            deletion.get(3,TimeUnit.SECONDS);assertThat(heartbeat.get(3,TimeUnit.SECONDS)).isTrue();
        }finally{release.countDown();}
    }
    void expire(){jdbc.update("UPDATE cleanup_job_leases SET expires_at='1970-01-01 00:00:00'");}

    @ParameterizedTest @ValueSource(strings={"/runs","/targets","/dry-run","/targets/1/attempts"})
    void adminReadApisRequireAuthentication(String path)throws Exception{mvc.perform(get("/api/admin/topic-cleanup"+path)).andExpect(status().isUnauthorized());}
    @Test void ordinaryUserCannotReadCleanup()throws Exception{mvc.perform(get("/api/admin/topic-cleanup/runs").with(user("owner@example.invalid"))).andExpect(status().isForbidden());}
    @Test void adminCanReadRunsAndReadOnlyDryRun()throws Exception{topic(721,true);mvc.perform(get("/api/admin/topic-cleanup/runs").with(user(ADMIN).roles("ADMIN"))).andExpect(status().isOk());mvc.perform(get("/api/admin/topic-cleanup/dry-run").with(user(ADMIN).roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$[0].reason").value("ELIGIBLE"));assertThat(count("topic_cleanup_runs")).isZero();}
    @Test void missingCsrfCannotResume()throws Exception{mvc.perform(post("/api/admin/topic-cleanup/targets/1/resume").with(user(ADMIN).roles("ADMIN")).contentType("application/json").content("{\"reason\":\"確認\"}")).andExpect(status().isForbidden());}
    @Test void inactiveAdminCannotReadEvenWithExistingAuthentication()throws Exception{jdbc.update("UPDATE users SET account_status='FROZEN' WHERE id=?",administrator);mvc.perform(get("/api/admin/topic-cleanup/runs").with(user(ADMIN).roles("ADMIN"))).andExpect(status().isForbidden());}
    @Test void emptyReasonIs400()throws Exception{mvc.perform(post("/api/admin/topic-cleanup/targets/1/resume").with(user(ADMIN).roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"reason\":\" \"}")).andExpect(status().isBadRequest());}
    @Test void missingResumeTargetIs404()throws Exception{mvc.perform(post("/api/admin/topic-cleanup/targets/1/resume").with(user(ADMIN).roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"reason\":\"確認\"}")).andExpect(status().isNotFound());}
    @Test void invalidPageIs400()throws Exception{mvc.perform(get("/api/admin/topic-cleanup/targets?page=-1").with(user(ADMIN).roles("ADMIN"))).andExpect(status().isBadRequest());}
    @Test void ordinaryUserCannotResumeEvenWithCsrf()throws Exception{mvc.perform(post("/api/admin/topic-cleanup/targets/1/resume").with(user("owner@example.invalid")).with(csrf()).contentType("application/json").content("{\"reason\":\"確認\"}")).andExpect(status().isForbidden());}
}
