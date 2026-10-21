package com.example.bulletinboard.controller.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.*;
import com.example.bulletinboard.model.*;
import com.example.bulletinboard.model.PrivacyRetentionRecord.Target;
import com.example.bulletinboard.repository.*;
import com.example.bulletinboard.security.AccountPrincipal;
import com.example.bulletinboard.service.*;
import com.example.bulletinboard.support.TopicImageFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 実際のAPI・Security・トランザクションと専用H2。実DB・メールを使用しない。 */
@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:withdrawal-privacy;MODE=MySQL;DATABASE_TO_LOWER=TRUE;LOCK_TIMEOUT=10000",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.open-in-view=false","spring.flyway.enabled=false","spring.sql.init.mode=never",
    "contact.retention.enabled=false","privacy.session-retry-delay-ms=3600000",
    "privacy.profile-image-deletion.enabled=true",
    "privacy.encryption.key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=","privacy.encryption.key-id=test-only"
})
@AutoConfigureMockMvc @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class WithdrawalPrivacyIntegrationTest {
    private static final String PASSWORD="Password123";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired ProfileRepository profiles;
    @Autowired ProfileImageRepository images;
    @Autowired TopicRepository topics;
    @Autowired AnswerRepository answers;
    @Autowired LikeRepository likes;
    @Autowired WinnerAchievementRepository achievements;
    @Autowired ReportRepository reports;
    @Autowired PrivacyRetentionRepository holds;
    @Autowired PrivacyAuditRepository audits;
    @Autowired WithdrawalTaskRepository tasks;
    @Autowired PasswordResetTokenRepository tokens;
    @Autowired CustomUserDetailsService registration;
    @Autowired WithdrawalService withdrawal;
    @Autowired PasswordResetService resets;
    @Autowired ProfileImageService profileImages;
    @Autowired ReportService reportService;
    @Autowired PrivacyRetentionService retention;
    @Autowired WithdrawalFollowupService followup;
    @Autowired WithdrawalMigrationPlanService migration;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean JavaMailSender mail;
    @MockitoSpyBean SessionRegistry sessions;
    @MockitoSpyBean PrivacyCrypto crypto;
    private User owner,viewer,reviewer,admin;

    @BeforeEach void setup() {
        owner=account(false,false);viewer=account(false,false);admin=account(true,false);reviewer=account(true,true);
    }
    @AfterEach void resetSpies() { reset(sessions,crypto); }

    @Test void withdrawalErasesCredentialsAndKeepsCommunityReferences() throws Exception {
        String oldEmail=owner.getEmail(),oldName=owner.getUsername(),oldHash=owner.getPassword();
        Long profileId=profiles.findByUserId(owner.getId()).orElseThrow().getId();
        var source=images.saveAndFlush(new ProfileImage(owner.getId(),"image/png",TopicImageFixtures.image("png")));
        var profile=profiles.findById(profileId).orElseThrow();profile.setBio("private bio");profile.setIcon(ProfileImageService.URL_PREFIX+source.getId());profiles.saveAndFlush(profile);
        Topic topic=topic(owner);Answer answer=answer(topic,owner);likes.saveAndFlush(new Like(viewer,answer));
        WinnerAchievement achievement=new WinnerAchievement();achievement.setUser(owner);achievement.setTopic(topic);
        achievement.setAnswer(answer);achievement.setLikeCount(1);achievement.setAchievedAt(LocalDateTime.now());achievements.saveAndFlush(achievement);
        resets.issue(oldEmail);
        withdraw(login(owner)).andExpect(status().isNoContent()).andExpect(content().string(""));
        User stored=users.findById(owner.getId()).orElseThrow();
        assertThat(stored.getEmail()).isEqualTo(WithdrawalIdentity.email(owner.getId())).isNotEqualTo(oldEmail);
        assertThat(stored.getUsername()).isEqualTo(WithdrawalIdentity.username(owner.getId())).isNotEqualTo(oldName);
        assertThat(stored.getPassword()).isNotEqualTo(oldHash);assertThat(passwords.matches(PASSWORD,stored.getPassword())).isFalse();
        assertThat(stored.getFailedAttempt()).isZero();assertThat(stored.isAccountNonLocked()).isTrue();
        assertThat(stored.getPersonalDataErasedAt()).isNotNull();assertThat(tokens.findByUserId(owner.getId())).isEmpty();
        var empty=profiles.findById(profileId).orElseThrow();assertThat(empty.getBio()).isNull();assertThat(empty.getIcon()).isNull();
        assertThat(images.existsById(source.getId())).isTrue();assertThat(tasks.findById(owner.getId()).orElseThrow().getImageStatus()).isEqualTo("POLICY_PENDING");
        assertThat(topics.existsById(topic.getId())).isTrue();assertThat(answers.existsById(answer.getId())).isTrue();
        assertThat(likes.findByUserAndAnswer(viewer,answer)).isPresent();
        assertThat(achievements.findByUserOrderByAchievedAtDesc(owner)).hasSize(1);
    }

    @Test void profileFailureRollsBackIdentityTokensAndTask() throws Exception {
        var issued=resets.issue(owner.getEmail()).orElseThrow();String oldHash=owner.getPassword();
        Long profileId=profiles.findByUserId(owner.getId()).orElseThrow().getId();
        var profile=profiles.findById(profileId).orElseThrow();profile.setBio("retain on failure");profiles.saveAndFlush(profile);
        jdbc.execute("ALTER TABLE profiles ADD CONSTRAINT privacy_test_failure CHECK (id <> "+profileId+" OR bio IS NOT NULL)");
        try { withdraw(login(owner)).andExpect(status().isInternalServerError()); }
        finally { jdbc.execute("ALTER TABLE profiles DROP CONSTRAINT privacy_test_failure"); }
        User stored=users.findById(owner.getId()).orElseThrow();assertThat(stored.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(stored.getEmail()).isEqualTo(owner.getEmail());assertThat(stored.getPassword()).isEqualTo(oldHash);
        assertThat(tokens.findByUserId(owner.getId())).isPresent();
        assertThat(tasks.existsById(owner.getId())).isFalse();
    }

    @Test void originalEmailAndUsernameCanRegisterOnlyAsNewUser() throws Exception {
        String oldEmail=owner.getEmail(),oldName=owner.getUsername();Topic topic=topic(owner);
        withdraw(login(owner)).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email",oldEmail,"username",oldName,"password",PASSWORD))))
                .andExpect(status().isCreated());
        User next=users.findByEmail(oldEmail).orElseThrow();assertThat(next.getId()).isNotEqualTo(owner.getId());
        assertThat(jdbc.queryForObject("SELECT user_id FROM topics WHERE id=?",Long.class,topic.getId())).isEqualTo(owner.getId());
    }

    @ParameterizedTest @ValueSource(strings={"a@withdrawn.invalid","a@INVALID","a@sub.InVaLiD"})
    void reservedDomainsCannotRegister(String email) throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email",email,"username","Valid123","password",PASSWORD))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test void legacySessionCannotBecomeReRegisteredUser() throws Exception {
        String original=owner.getEmail();MockHttpSession legacy=login(owner);
        var oldPrincipal=new org.springframework.security.core.userdetails.User(original,"unused",List.of(new SimpleGrantedAuthority("ROLE_USER")));
        legacy.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,new SecurityContextImpl(
                UsernamePasswordAuthenticationToken.authenticated(oldPrincipal,null,oldPrincipal.getAuthorities())));
        withdraw(login(owner)).andExpect(status().isNoContent());
        User next=new User();next.setUsername(owner.getUsername());next.setEmail(original);next.setPassword(PASSWORD);registration.registerUser(next);
        mvc.perform(get("/api/auth/me").session(legacy)).andExpect(status().isUnauthorized());
        assertThat(legacy.isInvalid()).isTrue();
    }

    @ParameterizedTest @ValueSource(strings={"old","placeholder","unknown"})
    void withdrawnUnknownAndWrongPasswordsShareLoginError(String which) throws Exception {
        String original=owner.getEmail();withdraw(login(owner)).andExpect(status().isNoContent());
        String email=switch(which){case "old" -> original;case "placeholder" -> WithdrawalIdentity.email(owner.getId());default -> "absent-"+UUID.randomUUID()+"@example.test";};
        mvc.perform(post("/api/auth/login").with(csrf()).param("email",email).param("password",PASSWORD))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.reason").value("WRONG_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("メールアドレスまたはパスワードが正しくありません。"));
    }

    @Test void oldResetTokenIsRemovedAndCannotUpdateRetiredAccount() throws Exception {
        String token=resets.issue(owner.getEmail()).orElseThrow().rawToken();
        withdraw(login(owner)).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/password-reset/confirm").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("token",token,"newPassword","NewPassword123"))))
                .andExpect(status().isBadRequest());
        assertThat(tokens.findByUserId(owner.getId())).isEmpty();
    }

    @Test void ordinaryAdminResponsesHideOldAndInternalIdentity() throws Exception {
        Topic topic=topic(owner);Answer answer=answer(topic,owner);
        Long profileId=profiles.findByUserId(owner.getId()).orElseThrow().getId();
        withdraw(login(owner)).andExpect(status().isNoContent());
        for(String path:List.of("/api/admin/users/"+owner.getId(),"/api/admin/topics/"+topic.getId(),
                "/api/admin/answers/"+answer.getId(),"/api/admin/profiles/"+profileId)) {
            String body=as(admin,get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(body).contains("退会済みユーザー").doesNotContain(owner.getEmail(),owner.getUsername(),"withdrawn_user_","@withdrawn.invalid");
        }
    }

    @ParameterizedTest @ValueSource(strings={"text","image"})
    void ordinaryAdminCannotReadEvidence(String kind) throws Exception {
        Long reportId=profileReport();
        String path=kind.equals("text") ? "/api/admin/privacy/reports/"+reportId+"/evidence" : "/api/admin/reports/"+reportId+"/evidence-image";
        as(admin,get(path)).andExpect(status().isForbidden());
        as(admin,get("/api/admin/reports/"+reportId)).andExpect(status().isOk()).andExpect(jsonPath("$.evidence").isEmpty());
    }

    @Test void reviewerReadsEvidenceWithAuditAndKeepsItAfterErasure() throws Exception {
        var original=images.saveAndFlush(new ProfileImage(owner.getId(),"image/png",TopicImageFixtures.image("png")));
        var profile=profiles.findByUserId(owner.getId()).orElseThrow();profile.setIcon(ProfileImageService.URL_PREFIX+original.getId());profiles.saveAndFlush(profile);
        Long reportId=profileReport();withdraw(login(owner)).andExpect(status().isNoContent());
        long before=audits.count();
        as(reviewer,get("/api/admin/privacy/reports/"+reportId+"/evidence")).andExpect(status().isOk())
                .andExpect(jsonPath("$.profileDisplayName").value(owner.getUsername()));
        as(reviewer,get("/api/admin/reports/"+reportId+"/evidence-image")).andExpect(status().isOk()).andExpect(content().bytes(original.getContent()));
        assertThat(audits.count()).isEqualTo(before+2);
    }

    @Test void retainedIdentityIsEncryptedAndOnlySelectedFieldCanBeRead() throws Exception {
        var hold=retention.create(identityHold(),key(reviewer));
        withdraw(login(owner)).andExpect(status().isNoContent());
        var record=holds.findById(hold.id()).orElseThrow();
        assertThat(new String(record.getEncryptedPayload(),java.nio.charset.StandardCharsets.UTF_8)).doesNotContain(owner.getEmail(),owner.getUsername());
        as(admin,get("/api/admin/privacy/retentions/"+hold.id()+"/identity")).andExpect(status().isForbidden());
        as(reviewer,get("/api/admin/privacy/retentions/"+hold.id()+"/identity")).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(owner.getEmail())).andExpect(jsonPath("$.username").doesNotExist());
    }

    @Test void missingKeyBlocksErasureWithActiveIdentityHold() throws Exception {
        retention.create(identityHold(),key(reviewer));MockHttpSession session=login(owner);
        doThrow(new IllegalStateException("Privacy key unavailable")).when(crypto).requireKey(anyString());
        withdraw(session).andExpect(status().isInternalServerError());
        User stored=users.findById(owner.getId()).orElseThrow();assertThat(stored.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(stored.getEmail()).isEqualTo(owner.getEmail());assertThat(tasks.existsById(owner.getId())).isFalse();
    }

    @Test void expiredHoldCannotBeReadButCanPurgeItsPayload() throws Exception {
        var hold=retention.create(identityHold(),key(reviewer));
        jdbc.update("UPDATE privacy_retention_records SET expires_at=? WHERE id=?",java.sql.Timestamp.valueOf(PrivacyRetentionService.now().minusSeconds(1)),hold.id());
        as(reviewer,get("/api/admin/privacy/retentions/"+hold.id()+"/identity")).andExpect(status().isForbidden());
        as(reviewer,delete("/api/admin/privacy/retentions/"+hold.id()+"/identity").with(csrf())).andExpect(status().isOk());
        assertThat(holds.findById(hold.id()).orElseThrow().getEncryptedPayload()).isNull();
    }

    @Test void pendingImagePolicyBlocksDeletionUntilExplicitlyResolved() throws Exception {
        var image=images.saveAndFlush(new ProfileImage(owner.getId(),"image/png",TopicImageFixtures.image("png")));
        withdraw(login(owner)).andExpect(status().isNoContent());followup.processImages(owner.getId());
        assertThat(images.existsById(image.getId())).isTrue();assertThat(tasks.findById(owner.getId()).orElseThrow().getImageStatus()).isEqualTo("BLOCKED");
        var hold=holds.findByTargetTypeAndTargetId(Target.PROFILE_IMAGE,image.getId()).getFirst();
        retention.setPolicy(hold.getId(),new PrivacyRetentionService.HoldRequest(Target.PROFILE_IMAGE,image.getId(),"approved removal review","test basis",OffsetDateTime.now(ZoneOffset.UTC).plusDays(1),Set.of()),key(reviewer));
        retention.release(hold.getId(),key(reviewer));followup.processImages(owner.getId());
        assertThat(images.existsById(image.getId())).isFalse();assertThat(tasks.findById(owner.getId()).orElseThrow().getImageStatus()).isEqualTo("DONE");
    }

    @Test void sessionFailureIsPersistedAndRetryDoesNotUndoWithdrawal() throws Exception {
        MockHttpSession session=login(owner);
        doThrow(new IllegalStateException("simulated registry failure")).when(sessions).getAllPrincipals();
        withdraw(session).andExpect(status().isNoContent());
        assertThat(tasks.findById(owner.getId()).orElseThrow().getSessionStatus()).isEqualTo("FAILED");
        assertThat(users.findById(owner.getId()).orElseThrow().getAccountStatus()).isEqualTo(AccountStatus.WITHDRAWN);
        doCallRealMethod().when(sessions).getAllPrincipals();followup.processSessions(owner.getId());
        assertThat(tasks.findById(owner.getId()).orElseThrow().getSessionStatus()).isEqualTo("DONE");
    }

    @Test void existingWithdrawnErasureIsIdempotent() {
        owner.setAccountStatus(AccountStatus.WITHDRAWN);owner.setWithdrawnAt(LocalDateTime.now().minusDays(2));users.saveAndFlush(owner);
        withdrawal.eraseExisting(owner.getId());User first=users.findById(owner.getId()).orElseThrow();
        withdrawal.eraseExisting(owner.getId());User again=users.findById(owner.getId()).orElseThrow();
        assertThat(again.getPassword()).isEqualTo(first.getPassword());assertThat(again.getPersonalDataErasedAt()).isEqualTo(first.getPersonalDataErasedAt());
        assertThat(again.getAuthenticationVersion()).isEqualTo(first.getAuthenticationVersion());
    }

    @Test void reservedValueCollisionRollsBackWithoutChangingOtherUser() throws Exception {
        User conflicting=account(false,false);conflicting.setEmail(WithdrawalIdentity.email(owner.getId()));users.saveAndFlush(conflicting);
        withdraw(login(owner)).andExpect(status().isInternalServerError());
        assertThat(users.findById(owner.getId()).orElseThrow().getEmail()).isEqualTo(owner.getEmail());
        assertThat(users.findById(conflicting.getId()).orElseThrow().getEmail()).isEqualTo(conflicting.getEmail());
    }

    @ParameterizedTest @ValueSource(strings={"/api/account/withdraw","/api/admin/privacy/retentions"})
    void mutationsStillRequireCsrf(String path) throws Exception {
        as(reviewer,post(path).contentType(MediaType.APPLICATION_JSON).content("{}")) .andExpect(status().isForbidden());
    }

    @Test void expiredPolicyCannotBeConfigured() throws Exception {
        var request=new PrivacyRetentionService.HoldRequest(Target.USER_IDENTITY,key(owner),"test purpose","test basis",OffsetDateTime.now().minusDays(1),Set.of(PrivacyRetentionService.IdentityField.EMAIL));
        as(reviewer,post("/api/admin/privacy/retentions").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test void releaseRevokesAccessAndRecordsChange() {
        var hold=retention.create(identityHold(),key(reviewer));long before=audits.count();retention.release(hold.id(),key(reviewer));
        assertThatThrownBy(() -> retention.identity(hold.id(),key(reviewer))).isInstanceOf(com.example.bulletinboard.exception.ForbiddenOperationException.class);
        assertThat(audits.count()).isEqualTo(before+1);
    }

    @Test void reservedUsernameCannotRegister() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email","new-"+UUID.randomUUID()+"@example.test","username","WITHDRAWN_USER_1","password",PASSWORD))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.username").exists());
    }

    @Test void resetVersionRejectsOldSessionsEvenWhenRegistryInvalidationFails() throws Exception {
        MockHttpSession old=login(owner);String raw=resets.issue(owner.getEmail()).orElseThrow().rawToken();
        // Registryから対象が見えなくても、DBの認証版数が古いSessionを拒否する。
        doReturn(List.of()).when(sessions).getAllPrincipals();resets.confirm(raw,"NewPassword123");
        mvc.perform(get("/api/topics").session(old)).andExpect(status().isForbidden());
    }

    @Test void previewNeverErasesActiveUser() {
        assertThat(migration.preview(List.of(owner.getId()),key(reviewer)).getFirst().status()).isEqualTo("NOT_WITHDRAWN");
        assertThat(users.findById(owner.getId()).orElseThrow().getEmail()).isEqualTo(owner.getEmail());
    }

    @Test void passwordResetRaceNeverRestoresCommittedWithdrawal() throws Exception {
        String raw=resets.issue(owner.getEmail()).orElseThrow().rawToken();
        AtomicBoolean withdrawn=new AtomicBoolean(),resetDone=new AtomicBoolean();
        race(() -> {
            try { withdrawal.withdraw(key(owner),PASSWORD);withdrawn.set(true); }
            catch(com.example.bulletinboard.exception.WithdrawalException ex) {
                assertThat(ex.getReason()).isEqualTo(com.example.bulletinboard.exception.WithdrawalException.Reason.PASSWORD_MISMATCH);
            }
        },() -> {
            try { resets.confirm(raw,"NewPassword123");resetDone.set(true); }
            catch(com.example.bulletinboard.exception.PasswordResetException ex) {
                assertThat(ex.getReason()).isEqualTo(com.example.bulletinboard.exception.PasswordResetException.Reason.INVALID);
            }
        });
        User finalUser=users.findById(owner.getId()).orElseThrow();
        if(withdrawn.get()) {
            assertThat(finalUser.getAccountStatus()).isEqualTo(AccountStatus.WITHDRAWN);
            assertThat(tokens.findByUserId(owner.getId())).isEmpty();
            assertThat(passwords.matches("NewPassword123",finalUser.getPassword())).isFalse();
        } else { assertThat(resetDone).isTrue();assertThat(finalUser.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE); }
    }

    @Test void tokenIssuanceRaceLeavesNoUsableTokenAfterWithdrawal() throws Exception {
        java.util.concurrent.atomic.AtomicReference<String> issued=new java.util.concurrent.atomic.AtomicReference<>();
        race(() -> withdrawal.withdraw(key(owner),PASSWORD),
                () -> resets.issue(owner.getEmail()).ifPresent(token -> issued.set(token.rawToken())));
        assertThat(users.findById(owner.getId()).orElseThrow().getAccountStatus()).isEqualTo(AccountStatus.WITHDRAWN);
        assertThat(tokens.findByUserId(owner.getId())).isEmpty();
        if(issued.get()!=null) assertThatThrownBy(() -> resets.confirm(issued.get(),"NewPassword123"))
                .isInstanceOf(com.example.bulletinboard.exception.PasswordResetException.class);
    }

    @Test void profileImageRaceCannotRepopulateWithdrawnProfile() throws Exception {
        race(() -> withdrawal.withdraw(key(owner),PASSWORD),() -> {
            try { profileImages.put(new TopicImageValidator.ValidatedImage(TopicImageFixtures.image("png"),"image/png"),key(owner)); }
            catch(com.example.bulletinboard.exception.ProfileOperationException ex) {
                assertThat(ex.getReason()).isEqualTo(com.example.bulletinboard.exception.ProfileOperationException.Reason.FORBIDDEN);
            }
        });
        assertThat(users.findById(owner.getId()).orElseThrow().getAccountStatus()).isEqualTo(AccountStatus.WITHDRAWN);
        assertThat(profiles.findByUserId(owner.getId()).orElseThrow().getIcon()).isNull();
    }

    @Test void imageDeleteFailureRollsBackAndCanBeRetried() throws Exception {
        var image=images.saveAndFlush(new ProfileImage(owner.getId(),"image/png",TopicImageFixtures.image("png")));
        withdraw(login(owner)).andExpect(status().isNoContent());
        var hold=holds.findByTargetTypeAndTargetId(Target.PROFILE_IMAGE,image.getId()).getFirst();
        retention.setPolicy(hold.getId(),new PrivacyRetentionService.HoldRequest(Target.PROFILE_IMAGE,image.getId(),"approved review","test basis",OffsetDateTime.now().plusDays(1),Set.of()),key(reviewer));
        retention.release(hold.getId(),key(reviewer));
        jdbc.execute("ALTER TABLE withdrawal_tasks ADD CONSTRAINT privacy_task_test_failure CHECK (user_id <> "+owner.getId()+" OR image_status <> 'DONE')");
        try { followup.processImages(owner.getId()); }
        finally { jdbc.execute("ALTER TABLE withdrawal_tasks DROP CONSTRAINT privacy_task_test_failure"); }
        assertThat(images.existsById(image.getId())).isTrue();assertThat(tasks.findById(owner.getId()).orElseThrow().getImageStatus()).isEqualTo("FAILED");
        followup.processImages(owner.getId());assertThat(images.existsById(image.getId())).isFalse();
        assertThat(tasks.findById(owner.getId()).orElseThrow().getImageStatus()).isEqualTo("DONE");
    }

    private void race(Runnable first,Runnable second) throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {
            var one=pool.submit(() -> {await(start);first.run();});var two=pool.submit(() -> {await(start);second.run();});
            start.countDown();one.get(30,TimeUnit.SECONDS);two.get(30,TimeUnit.SECONDS);
        } finally {pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
    }
    private void await(CountDownLatch latch) {
        try {if(!latch.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Test barrier timeout");}
        catch(InterruptedException ex){Thread.currentThread().interrupt();throw new IllegalStateException(ex);}
    }
    @Test void previewFindsAlreadyErasedUser() {
        owner.setAccountStatus(AccountStatus.WITHDRAWN);users.saveAndFlush(owner);withdrawal.eraseExisting(owner.getId());
        assertThat(migration.preview(List.of(owner.getId()),key(reviewer)).getFirst().status()).isEqualTo("ALREADY_ERASED");
    }
    @Test void previewDetectsCollisionWithoutExposingIdentity() {
        owner.setAccountStatus(AccountStatus.WITHDRAWN);users.saveAndFlush(owner);User conflict=account(false,false);
        conflict.setUsername(WithdrawalIdentity.username(owner.getId()));users.saveAndFlush(conflict);
        var result=migration.preview(List.of(owner.getId()),key(reviewer)).getFirst();assertThat(result.status()).isEqualTo("COLLISION");
        assertThat(result.toString()).doesNotContain(owner.getUsername(),owner.getEmail(),conflict.getUsername());
    }

    private User account(boolean isAdmin,boolean privacy) {
        User user=new User();String suffix=UUID.randomUUID().toString();
        user.setUsername("u1"+suffix.substring(0,6));user.setEmail(suffix+"@example.test");user.setPassword(PASSWORD);
        user.setRole(isAdmin ? "ROLE_ADMIN" : "ROLE_USER");user.setPrivacyReviewer(privacy);registration.registerUser(user);return user;
    }
    private String key(User user) { return user.getId().toString(); }
    private AccountPrincipal principal(User user) {
        User latest=users.findById(user.getId()).orElseThrow();
        return new AccountPrincipal(latest.getId(),"unused",true,true,List.of(new SimpleGrantedAuthority(latest.getRole())),latest.getAuthenticationVersion());
    }
    private ResultActions as(User actor,org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.with(user(principal(actor))));
    }
    private MockHttpSession login(User actor) throws Exception {
        return (MockHttpSession)mvc.perform(post("/api/auth/login").with(csrf()).param("email",actor.getEmail()).param("password",PASSWORD))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    private ResultActions withdraw(MockHttpSession session) throws Exception {
        return mvc.perform(post("/api/account/withdraw").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\""+PASSWORD+"\",\"confirmed\":true}"));
    }
    private Topic topic(User owner) {
        Topic topic=new Topic();topic.setUser(owner);topic.setTitle("retained topic");topic.setQuestion("retained question");
        topic.setImage("/api/topic-images/11111111-1111-1111-1111-111111111111");return topics.saveAndFlush(topic);
    }
    private Answer answer(Topic topic,User owner) {
        Answer answer=new Answer();answer.setTopic(topic);answer.setUser(owner);answer.setContent("retained answer");return answers.saveAndFlush(answer);
    }
    private Long profileReport() {
        Long profileId=profiles.findByUserId(owner.getId()).orElseThrow().getId();
        reportService.submitProfile(profileId,key(viewer),new com.example.bulletinboard.dto.report.ReportRequest(ReportReason.SPAM,null));
        return reports.findByReporterUserId(viewer.getId(),org.springframework.data.domain.Pageable.unpaged()).getContent().stream()
                .filter(report -> report.getTargetId().equals(profileId)).findFirst().orElseThrow().getId();
    }
    private PrivacyRetentionService.HoldRequest identityHold() {
        return new PrivacyRetentionService.HoldRequest(Target.USER_IDENTITY,key(owner),"specific test investigation","test authorization",OffsetDateTime.now(ZoneOffset.UTC).plusDays(1),Set.of(PrivacyRetentionService.IdentityField.EMAIL));
    }
}
