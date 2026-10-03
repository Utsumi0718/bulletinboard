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

import java.util.Map;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.bulletinboard.exception.handler.GlobalExceptionHandler;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.TopicImageRepository;
import com.example.bulletinboard.repository.TopicRepository;
import com.example.bulletinboard.repository.UserRepository;
import com.example.bulletinboard.security.SecurityConfig;
import com.example.bulletinboard.service.CustomUserDetailsService;
import com.example.bulletinboard.service.TopicImageService;
import com.example.bulletinboard.service.TopicImageValidator;
import com.example.bulletinboard.service.TopicService;
import com.example.bulletinboard.support.TopicImageFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 専用H2で実際のController・Service・Repository・Securityを接続する。
 * テスト全体を@Transactionalで包まず、Service終了後に別トランザクションで読み直す。
 * 初期データ投入・定期処理・メール送信を起動しない。MySQL・Flyway・実ブラウザは対象外。
 */
@SpringBootTest(classes = TopicImageIntegrationTest.ImageConfiguration.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:topic-image-flow;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=false", "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TopicImageIntegrationTest {
    @TestComponent
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = User.class)
    @EnableJpaRepositories(basePackageClasses = UserRepository.class)
    @Import({TopicApiController.class, TopicImageApiController.class, TopicService.class,
            TopicImageService.class, TopicImageValidator.class, CustomUserDetailsService.class,
            SecurityConfig.class, GlobalExceptionHandler.class})
    static class ImageConfiguration { }

    private static final String OWNER = "owner@example.com";
    private static final String OTHER = "other@example.com";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired TopicRepository topics;
    @Autowired TopicImageRepository images;
    @Autowired TopicImageService imageService;
    @Autowired TopicImageValidator validator;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        topics.deleteAll();
        images.deleteAll();
        users.deleteAll();
        users.saveAndFlush(account("owner", OWNER));
        users.saveAndFlush(account("other", OTHER));
    }

    @Test
    void uploadCreateReplaceDeleteKeepsOriginalBytesAndControlsVisibility() throws Exception {
        byte[] original = TopicImageFixtures.image("png");
        String first = upload(OWNER, "png");
        mvc.perform(get(first).with(user(OWNER))).andExpect(status().isOk()).andExpect(content().bytes(original));
        mvc.perform(get(first).with(user(OTHER))).andExpect(status().isNotFound());

        long topicId = create(first);
        mvc.perform(get(first).with(user(OTHER))).andExpect(status().isOk()).andExpect(content().bytes(original));

        String second = upload(OWNER, "jpeg");
        mvc.perform(put("/api/topics/{id}", topicId).with(user(OWNER)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body(second))).andExpect(status().isOk());
        assertThat(second).isNotEqualTo(first);
        mvc.perform(get(first).with(user(OTHER))).andExpect(status().isNotFound());
        mvc.perform(get(second).with(user(OTHER))).andExpect(status().isOk());

        mvc.perform(delete("/api/topics/{id}", topicId).with(user(OWNER)).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get(second).with(user(OTHER))).andExpect(status().isNotFound());
        mvc.perform(get(first).with(user(OWNER))).andExpect(status().isOk()).andExpect(content().bytes(original));

        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            assertThat(topics.findById(topicId).orElseThrow().getDeletedAt()).isNotNull();
            assertThat(images.findById(first.substring(TopicImageService.URL_PREFIX.length())).orElseThrow().getContent())
                    .isEqualTo(original);
            assertThat(images.count()).isEqualTo(2);
        });
    }

    @Test
    void cannotCreateTopicUsingAnotherUsersImage() throws Exception {
        String url = upload(OTHER, "png");
        mvc.perform(post("/api/topics").with(user(OWNER)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body(url))).andExpect(status().isBadRequest());
        assertThat(topics.count()).isZero();
    }

    @Test
    void cannotCreateTopicUsingExternalUrl() throws Exception {
        mvc.perform(post("/api/topics").with(user(OWNER)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body("https://example.com/image.png")))
                .andExpect(status().isBadRequest());
        assertThat(topics.count()).isZero();
        assertThat(images.count()).isZero();
    }

    @Test
    void rejectedReplacementDoesNotChangeTopic() throws Exception {
        String first = upload(OWNER, "png");
        long id = create(first);
        String foreign = upload(OTHER, "jpeg");
        mvc.perform(put("/api/topics/{id}", id).with(user(OWNER)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body(foreign))).andExpect(status().isBadRequest());
        new TransactionTemplate(transactionManager).executeWithoutResult(tx ->
                assertThat(topics.findById(id).orElseThrow().getImage()).isEqualTo(first));
    }

    @Test
    void databaseFailureAfterImageFlushRollsBackImage() {
        // H2の実制約違反で、flush済み画像も同じトランザクションから消えることを確認する。
        // 実際のコミット時通信断を再現するテストではない。
        var validated = validator.validate(new MockMultipartFile("file", TopicImageFixtures.image("png")));
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            imageService.upload(validated, OWNER);
            images.flush();
            users.saveAndFlush(account("duplicate", OWNER));
        })).isInstanceOf(DataIntegrityViolationException.class);
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> assertThat(images.count()).isZero());
    }

    private User account(String name, String email) {
        User user = new User();
        user.setUsername(name);
        user.setEmail(email);
        user.setPassword("test-only-not-used-for-login");
        return user;
    }

    private String upload(String email, String format) throws Exception {
        var result = mvc.perform(multipart("/api/topic-images")
                .file(new MockMultipartFile("file", "image." + format, "image/" + format, TopicImageFixtures.image(format)))
                .with(user(email)).with(csrf())).andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).path("url").asText();
    }

    private long create(String url) throws Exception {
        var result = mvc.perform(post("/api/topics").with(user(OWNER)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body(url))).andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
    }

    private String body(String url) throws Exception {
        return mapper.writeValueAsString(Map.of("title", "画像のお題", "question", "何をしている？", "image", url));
    }
}
