package com.example.bulletinboard.controller.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.bulletinboard.exception.handler.GlobalExceptionHandler;
import com.example.bulletinboard.model.AdminOperationLog;
import com.example.bulletinboard.model.ContactStatus;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.AdminOperationLogRepository;
import com.example.bulletinboard.repository.ContactRepository;
import com.example.bulletinboard.repository.UserRepository;
import com.example.bulletinboard.security.SecurityConfig;
import com.example.bulletinboard.service.AdminContactService;
import com.example.bulletinboard.service.ContactNotificationService;
import com.example.bulletinboard.service.ContactService;
import com.example.bulletinboard.service.ContactSubmissionService;
import com.example.bulletinboard.service.CustomUserDetailsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 受付から管理APIでの削除まで同じデータを扱い、削除後にも操作履歴が残ることを確認します。
 * 実際のController・Service・Repository・Security設定を使い、メール通知だけをモック化します。
 * テスト全体をトランザクションで包まず、Service終了後に別トランザクションでDBを読み直します。
 * 専用H2とcreate-dropを使用し、コンテキスト終了時に破棄します。
 * 実ブラウザ・ログイン操作・MySQL・Flywayの検証は含みません。
 */
@SpringBootTest(classes = ContactAdminFlowIntegrationTest.FlowConfiguration.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:contact-admin-flow;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ContactAdminFlowIntegrationTest {

    // コンポーネントスキャンを使わず、定期処理や初期データ投入用のBeanを読み込まない。
    // 他テストのアプリケーションスキャンにもこの専用設定を混入させない。
    @TestComponent
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = User.class)
    @EnableJpaRepositories(basePackageClasses = UserRepository.class)
    @Import({ContactApiController.class, AdminContactApiController.class,
            ContactService.class, ContactSubmissionService.class, AdminContactService.class,
            SecurityConfig.class, GlobalExceptionHandler.class, CustomUserDetailsService.class})
    static class FlowConfiguration {
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private ContactRepository contactRepository;
    @Autowired private AdminOperationLogRepository operationLogRepository;
    @Autowired private CustomUserDetailsService userDetailsService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoBean
    private ContactNotificationService notificationService;

    @Test
    @DisplayName("匿名受付から管理一覧・詳細・状態変更・削除まで同じデータを扱い履歴2件を保持する")
    void submitThenManageAndDelete_shouldKeepBothOperationLogs() throws Exception {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        String suffix = UUID.randomUUID().toString();
        String adminEmail = "flow-admin-" + suffix + "@example.com";
        Long adminId = transactions.execute(tx -> {
            User admin = new User();
            admin.setUsername("flow-admin-" + suffix);
            admin.setEmail(adminEmail);
            admin.setPassword(passwordEncoder.encode("flow-test-password"));
            admin.setRole("ROLE_ADMIN");
            return userRepository.saveAndFlush(admin).getId();
        });
        // 保存済みUserから認証情報を作り、認証名とDBのメールアドレスを一致させる。
        UserDetails admin = userDetailsService.loadUserByUsername(adminEmail);
        String name = "フロー確認ユーザー";
        String email = "flow-contact-" + suffix + "@example.com";
        String subject = "受付から削除まで " + suffix;
        String message = "同じお問い合わせの状態変更と削除後の履歴を確認します。";

        mockMvc.perform(post("/api/contacts").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name, "email", email, "subject", subject, "message", message))))
                .andExpect(status().isCreated());

        // IDや並び順を仮定せず、このテストで送信した件名で特定する。
        List<JsonNode> matches = listContacts(admin).stream()
                .filter(item -> subject.equals(item.path("subject").asText())).toList();
        assertThat(matches).hasSize(1);
        long contactId = matches.get(0).path("id").asLong();

        mockMvc.perform(get("/api/admin/contacts/{id}", contactId).with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(contactId))
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.subject").value(subject))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.status").value("UNANSWERED"));

        mockMvc.perform(patch("/api/admin/contacts/{id}/status", contactId)
                        .with(user(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        transactions.executeWithoutResult(tx -> {
            assertThat(contactRepository.findById(contactId).orElseThrow().getStatus())
                    .isEqualTo(ContactStatus.IN_PROGRESS);
            assertThat(contactLogs(contactId)).singleElement().satisfies(log ->
                    assertLog(log, adminId, adminEmail, "STATUS_CHANGE", "UNANSWERED", "IN_PROGRESS"));
        });

        mockMvc.perform(delete("/api/admin/contacts/{id}", contactId).with(user(admin)).with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(listContacts(admin)).noneMatch(item -> item.path("id").asLong() == contactId);
        mockMvc.perform(get("/api/admin/contacts/{id}", contactId).with(user(admin)))
                .andExpect(status().isNotFound());

        transactions.executeWithoutResult(tx -> {
            assertThat(contactRepository.findById(contactId)).isEmpty();
            List<AdminOperationLog> logs = contactLogs(contactId);
            assertThat(logs).hasSize(2);
            assertThat(logs).filteredOn(log -> "STATUS_CHANGE".equals(log.getAction()))
                    .singleElement().satisfies(log ->
                            assertLog(log, adminId, adminEmail, "STATUS_CHANGE", "UNANSWERED", "IN_PROGRESS"));
            assertThat(logs).filteredOn(log -> "DELETE".equals(log.getAction()))
                    .singleElement().satisfies(log ->
                            assertLog(log, adminId, adminEmail, "DELETE", "IN_PROGRESS", null));
        });
    }

    private List<JsonNode> listContacts(UserDetails admin) throws Exception {
        String body = mockMvc.perform(get("/api/admin/contacts").param("size", "100").with(user(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode page = objectMapper.readTree(body);
        // 専用DBの一覧を1ページで取得できることも確認し、未取得ページの見落としを防ぐ。
        assertThat(page.path("totalElements").asLong()).isLessThanOrEqualTo(100L);
        assertThat(page.path("content").isArray()).isTrue();
        return StreamSupport.stream(page.path("content").spliterator(), false).toList();
    }

    // 呼び出し側の新しいトランザクション内で、対象IDの履歴をDBから読み直す。
    private List<AdminOperationLog> contactLogs(long contactId) {
        return operationLogRepository.findAll().stream()
                .filter(log -> "CONTACT".equals(log.getTargetType()) && log.getTargetId().equals(contactId))
                .toList();
    }

    private void assertLog(AdminOperationLog log, Long adminId, String adminEmail,
            String action, String beforeStatus, String afterStatus) {
        assertThat(log.getAdminUser().getId()).isEqualTo(adminId);
        assertThat(log.getAdminUser().getEmail()).isEqualTo(adminEmail);
        assertThat(log.getAction()).isEqualTo(action);
        assertThat(log.getBeforeStatus()).isEqualTo(beforeStatus);
        assertThat(log.getAfterStatus()).isEqualTo(afterStatus);
        assertThat(log.getResult()).isEqualTo("SUCCESS");
    }
}
