package com.example.bulletinboard.controller.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static com.example.bulletinboard.support.TestActors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import com.example.bulletinboard.support.WithAccount;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.example.bulletinboard.dto.contact.ContactRequest;
import com.example.bulletinboard.exception.ContactRateLimitException;
import com.example.bulletinboard.model.*;
import com.example.bulletinboard.repository.ContactRepository;
import com.example.bulletinboard.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 実Security・Service・Repository、専用H2。通知だけをモックとし外部送信しない。 */
@SpringBootTest(classes=ContactAdminFlowIntegrationTest.FlowConfiguration.class, properties={
        "spring.datasource.url=jdbc:h2:mem:contact-policy;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false", "spring.flyway.enabled=false", "spring.sql.init.mode=never"})
@AutoConfigureMockMvc
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@org.junit.jupiter.api.extension.ExtendWith(com.example.bulletinboard.support.TestActorExtension.class)
class ContactPolicyIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ContactRateLimitService limiter;
    @Autowired ContactRepository contacts;
    @Autowired ContactSubmissionService submission;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired PlatformTransactionManager manager;
    @Autowired com.example.bulletinboard.repository.UserRepository users;
    @MockitoBean ContactNotificationService notifications;

    @BeforeEach void clear() {
        contacts.deleteAll(); jdbc.update("DELETE FROM contact_rate_limit_buckets");
        if(users.findByEmail("policy-admin@example.test").isEmpty()) {
            var actor=new User();actor.setUsername("policy-admin");actor.setEmail("policy-admin@example.test");
            actor.setPassword("test-only");actor.setRole("ROLE_ADMIN");users.saveAndFlush(actor);
        }
    }
    private void send(String ip, String body, int status) throws Exception {
        mvc.perform(post("/api/contacts").with(csrf()).with(r->{r.setRemoteAddr(ip);return r;})
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(status));
    }
    private String valid() throws Exception { return mapper.writeValueAsString(Map.of("email","reply@example.com","category","BUG_REPORT","subject","相談","message","本文")); }
    private ContactRequest request() {
        var r=new ContactRequest(); r.setCategory(ContactCategory.BUG_REPORT); r.setEmail("reply@example.com"); r.setSubject("相談"); r.setMessage("本文"); return r;
    }
    @Test void allowsFiveAndRejectsSixth() {
        for(int i=0;i<5;i++) limiter.check("192.0.2.1");
        assertThatThrownBy(()->limiter.check("192.0.2.1")).isInstanceOf(ContactRateLimitException.class);
    }
    @Test void separatesSourceAddresses() {
        for(int i=0;i<5;i++) limiter.check("192.0.2.1");
        assertThatCode(()->limiter.check("192.0.2.2")).doesNotThrowAnyException();
    }
    @Test void freesSlotsAfterTenMinutes() {
        for(int i=0;i<5;i++) limiter.check("192.0.2.1");
        var old=jdbc.queryForObject("SELECT CURRENT_TIMESTAMP",Timestamp.class).toLocalDateTime().minusMinutes(10);
        jdbc.update("UPDATE contact_rate_limit_buckets SET slot1=?,slot2=?,slot3=?,slot4=?,slot5=?",old,old,old,old,old);
        assertThatCode(()->limiter.check("192.0.2.1")).doesNotThrowAnyException();
    }
    @Test void slidingWindowOnlyReleasesExpiredSlots() {
        for(int i=0;i<5;i++) limiter.check("192.0.2.1");
        var old=jdbc.queryForObject("SELECT CURRENT_TIMESTAMP",Timestamp.class).toLocalDateTime().minusMinutes(11);
        jdbc.update("UPDATE contact_rate_limit_buckets SET slot1=?",old);
        limiter.check("192.0.2.1");
        assertThatThrownBy(()->limiter.check("192.0.2.1")).isInstanceOf(ContactRateLimitException.class);
    }
    @Test void concurrentFirstRequestsCannotExceedFive() throws Exception {
        int count=16; var start=new CountDownLatch(1); var pool=Executors.newFixedThreadPool(count);
        try {
            List<Future<Boolean>> results=new ArrayList<>();
            for(int i=0;i<count;i++) results.add(pool.submit(()->{
                start.await(); try {limiter.check("192.0.2.1");return true;} catch(ContactRateLimitException ex){return false;}
            }));
            start.countDown(); int accepted=0;
            for(var result:results) if(result.get(30,TimeUnit.SECONDS)) accepted++;
            assertThat(accepted).isEqualTo(5);
        } finally {pool.shutdownNow(); assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void expiresStorageWithoutKeepingAnIpMap() {
        limiter.check("192.0.2.1"); limiter.check("192.0.2.2");
        jdbc.update("UPDATE contact_rate_limit_buckets SET expires_at=? WHERE slot2 IS NULL",Timestamp.valueOf(LocalDateTime.now().minusHours(1)));
        limiter.cleanExpired();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM contact_rate_limit_buckets",Integer.class)).isZero();
        limiter.check("192.0.2.1");
        assertThat(jdbc.queryForObject("SELECT source_key FROM contact_rate_limit_buckets",String.class)).hasSize(64).doesNotContain("192.0.2");
    }
    @Test void independentServiceInstancesUseTheSameDatabaseWindow() {
        for(int i=0;i<5;i++) limiter.check("192.0.2.1");
        var other=new ContactRateLimitService(jdbc); var tx=new TransactionTemplate(manager);
        assertThatThrownBy(()->tx.executeWithoutResult(t->other.check("192.0.2.1"))).isInstanceOf(ContactRateLimitException.class);
    }
    @Test void acceptedLimitSlotSurvivesCallerRollback() {
        var tx=new TransactionTemplate(manager);
        tx.executeWithoutResult(t->{limiter.check("192.0.2.1");t.setRollbackOnly();});
        for(int i=0;i<4;i++) limiter.check("192.0.2.1");
        assertThatThrownBy(()->limiter.check("192.0.2.1")).isInstanceOf(ContactRateLimitException.class);
    }
    @Test void invalidJsonConsumesSlotsButDoesNotSaveOrNotify() throws Exception {
        for(int i=0;i<5;i++) send("192.0.2.1","{",400);
        send("192.0.2.1",valid(),429);
        assertThat(contacts.count()).isZero(); verifyNoInteractions(notifications);
    }
    @Test void csrfFailuresDoNotConsumeSlots() throws Exception {
        for(int i=0;i<6;i++) mvc.perform(post("/api/contacts").contentType(MediaType.APPLICATION_JSON).content(valid())).andExpect(status().isForbidden());
        for(int i=0;i<5;i++) send("127.0.0.1",valid(),201);
        assertThat(contacts.count()).isEqualTo(5);
    }
    @Test void forgedForwardingHeadersCannotChangeSourceKey() throws Exception {
        for(int i=0;i<5;i++) mvc.perform(post("/api/contacts").with(csrf()).with(r->{r.setRemoteAddr("192.0.2.1");return r;})
                .header("X-Forwarded-For","198.51.100."+i).header("Forwarded","for=203.0.113."+i)
                .contentType(MediaType.APPLICATION_JSON).content(valid())).andExpect(status().isCreated());
        send("192.0.2.1",valid(),429); assertThat(contacts.count()).isEqualTo(5);
    }
    @ParameterizedTest @EnumSource(value=AccountStatus.class,names={"FROZEN","WITHDRAWN"})
    void nonActiveExistingSessionCanUsePublicContactEndpoint(AccountStatus accountStatus) throws Exception {
        // publicPathはアカウント状態照会を行わない。実セッション用認証情報を保持した場合も受付可能。
        String email="state-"+UUID.randomUUID()+"@example.com";
        var savedUser=new User(); savedUser.setUsername("state-"+UUID.randomUUID()); savedUser.setEmail(email);
        savedUser.setPassword("hash"); savedUser.setAccountStatus(accountStatus); users.saveAndFlush(savedUser);
        var auth=org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                email,null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")));
        auth.setDetails(new org.springframework.security.web.authentication.WebAuthenticationDetails(new org.springframework.mock.web.MockHttpServletRequest()));
        var context=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext(); context.setAuthentication(auth);
        var session=new org.springframework.mock.web.MockHttpSession();
        session.setAttribute(org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,context);
        // DBに保存した非ACTIVE Userと認証名を一致させる。
        mvc.perform(post("/api/contacts").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(valid())).andExpect(status().isCreated());
    }
    @Test @WithAccount(username="policy-admin@example.test",roles="ADMIN") void newCategoryIsPersistedAndVisibleToAdmin() throws Exception {
        submission.submitContact(request()); var stored=contacts.findAll().getFirst();
        assertThat(stored.getName()).isNull(); assertThat(stored.getCategory()).isEqualTo(ContactCategory.BUG_REPORT);
        mvc.perform(get("/api/admin/contacts")).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].category").value("BUG_REPORT"));
        mvc.perform(get("/api/admin/contacts/{id}",stored.getId())).andExpect(status().isOk()).andExpect(jsonPath("$.category").value("BUG_REPORT"));
    }
    @Test @WithAccount(username="policy-admin@example.test",roles="ADMIN") void legacyNameRemainsAndCategoryStaysUnknown() throws Exception {
        var contact=new Contact();contact.setName("過去の名前");contact.setEmail("legacy@example.com");contact.setSubject("過去");contact.setMessage("本文");
        long id=contacts.saveAndFlush(contact).getId();
        mvc.perform(get("/api/admin/contacts/{id}",id)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("過去の名前"))
                .andExpect(jsonPath("$.category").isEmpty());
        mvc.perform(get("/api/admin/contacts")).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].name").value("過去の名前"));
    }
    @Test void notificationFailureStillReturns201AndCommitsCategory() throws Exception {
        doThrow(new org.springframework.mail.MailSendException("fake secret")).when(notifications).sendNotification(any());
        send("192.0.2.1",valid(),201);
        assertThat(contacts.findAll()).singleElement().satisfies(c->{assertThat(c.getCategory()).isEqualTo(ContactCategory.BUG_REPORT);assertThat(c.getName()).isNull();});
    }
}
