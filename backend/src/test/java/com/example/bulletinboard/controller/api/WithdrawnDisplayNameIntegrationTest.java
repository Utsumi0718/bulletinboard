package com.example.bulletinboard.controller.api;

import static org.assertj.core.api.Assertions.assertThat;
import static com.example.bulletinboard.support.TestActors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.example.bulletinboard.exception.handler.GlobalExceptionHandler;
import com.example.bulletinboard.model.AccountStatus;
import com.example.bulletinboard.model.Answer;
import com.example.bulletinboard.model.Profile;
import com.example.bulletinboard.model.Topic;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.AnswerRepository;
import com.example.bulletinboard.repository.ProfileRepository;
import com.example.bulletinboard.repository.TopicRepository;
import com.example.bulletinboard.repository.UserRepository;
import com.example.bulletinboard.security.SecurityConfig;
import com.example.bulletinboard.service.AdminUserService;
import com.example.bulletinboard.service.AnswerService;
import com.example.bulletinboard.service.CustomUserDetailsService;
import com.example.bulletinboard.service.LikeService;
import com.example.bulletinboard.service.ProfileImageService;
import com.example.bulletinboard.service.ProfileImageValidator;
import com.example.bulletinboard.service.TopicImageService;
import com.example.bulletinboard.service.TopicImageValidator;
import com.example.bulletinboard.service.TopicService;

/** 専用H2・実Controller/Service/Securityで公開名と管理情報の境界を確認する。 */
@SpringBootTest(classes = WithdrawnDisplayNameIntegrationTest.FlowConfiguration.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:withdrawn-display-name;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=false", "spring.sql.init.mode=never", "contact.retention.enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@org.junit.jupiter.api.extension.ExtendWith(com.example.bulletinboard.support.TestActorExtension.class)
class WithdrawnDisplayNameIntegrationTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = User.class)
    @EnableJpaRepositories(basePackageClasses = UserRepository.class)
    @Import({TopicApiController.class, TopicService.class, TopicImageService.class,
            AnswerApiController.class, AnswerService.class, LikeService.class,
            ProfileApiController.class, ProfileImageService.class, ProfileImageValidator.class,
            TopicImageValidator.class, AdminUserApiController.class, AdminUserService.class,
            CustomUserDetailsService.class, SecurityConfig.class, GlobalExceptionHandler.class})
    static class FlowConfiguration { }

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TopicRepository topics;
    @Autowired AnswerRepository answers;
    @Autowired ProfileRepository profiles;
    private User viewer;

    @BeforeEach
    void setup() {
        viewer = account(AccountStatus.ACTIVE);
    }

    @ParameterizedTest
    @EnumSource(AccountStatus.class)
    void topicDetailUsesPublicName(AccountStatus state) throws Exception {
        User owner = account(state);
        Topic topic = topic(owner);
        var result = asViewer("/api/topics/" + topic.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(expected(owner))).andReturn();
        if (state == AccountStatus.WITHDRAWN) {
            assertThat(result.getResponse().getContentAsString()).doesNotContain(owner.getUsername());
        }
        assertThat(users.findById(owner.getId()).orElseThrow().getUsername()).isEqualTo(owner.getUsername());
    }

    @Test
    void topicListAndSearchKeepTheirExistingContractWithoutNames() throws Exception {
        User owner = account(AccountStatus.WITHDRAWN);
        Topic topic = topic(owner);
        String keyword = "topic-" + UUID.randomUUID();
        topic.setTitle(keyword);
        topics.saveAndFlush(topic);
        for (String url : new String[] {"/api/topics", "/api/topics?keyword=" + keyword}) {
            String body = asViewer(url).andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[*].username").doesNotExist())
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain(owner.getUsername());
        }
    }

    @Test
    void answerListMasksMultipleWithdrawnOwnersAndKeepsOtherNames() throws Exception {
        Topic topic = topic(viewer);
        User active = account(AccountStatus.ACTIVE);
        User frozen = account(AccountStatus.FROZEN);
        User first = account(AccountStatus.WITHDRAWN);
        User second = account(AccountStatus.WITHDRAWN);
        Answer a = answer(topic, active), f = answer(topic, frozen);
        Answer w1 = answer(topic, first), w2 = answer(topic, second);
        var result = asViewer("/api/topics/" + topic.getId() + "/answers")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + a.getId() + ")].username").value(active.getUsername()))
                .andExpect(jsonPath("$[?(@.id == " + f.getId() + ")].username").value(frozen.getUsername()))
                .andExpect(jsonPath("$[?(@.id == " + w1.getId() + ")].username").value("退会済みユーザー"))
                .andExpect(jsonPath("$[?(@.id == " + w2.getId() + ")].username").value("退会済みユーザー"))
                .andReturn();
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(first.getUsername(), second.getUsername());
    }

    @ParameterizedTest
    @EnumSource(AccountStatus.class)
    void profileKeepsExistingVisibility(AccountStatus state) throws Exception {
        User owner = account(state);
        Profile profile = new Profile();
        profile.setUser(owner);
        profiles.saveAndFlush(profile);
        var result = asViewer("/api/profiles/" + profile.getId());
        if (state == AccountStatus.WITHDRAWN) {
            String body = result.andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain(owner.getUsername());
        } else {
            result.andExpect(status().isOk()).andExpect(jsonPath("$.username").value(owner.getUsername()));
        }
    }

    @Test
    void ordinaryAdminDetailMasksWithdrawnIdentity() throws Exception {
        User owner = account(AccountStatus.WITHDRAWN);
        User admin = account(AccountStatus.ACTIVE);
        admin.setRole("ROLE_ADMIN");
        users.saveAndFlush(admin);
        mvc.perform(get("/api/admin/users/{id}", owner.getId()).with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("退会済みユーザー"))
                .andExpect(jsonPath("$.accountStatus").value("WITHDRAWN"));
    }

    private ResultActions asViewer(String url) throws Exception {
        return mvc.perform(get(url).with(user(viewer.getEmail())));
    }

    private User account(AccountStatus state) {
        String suffix = UUID.randomUUID().toString();
        User user = new User();
        user.setUsername("u-" + suffix);
        user.setEmail(suffix + "@example.invalid");
        user.setPassword("unused-test-hash");
        user.setAccountStatus(state);
        return users.saveAndFlush(user);
    }

    private Topic topic(User owner) {
        Topic topic = new Topic();
        topic.setUser(owner);
        topic.setTitle("test topic");
        topic.setImage("/images/test.png");
        topic.setQuestion("test question");
        return topics.saveAndFlush(topic);
    }

    private Answer answer(Topic topic, User owner) {
        Answer answer = new Answer();
        answer.setTopic(topic);
        answer.setUser(owner);
        answer.setContent("test answer");
        return answers.saveAndFlush(answer);
    }

    private String expected(User owner) {
        return owner.getAccountStatus() == AccountStatus.WITHDRAWN ? "退会済みユーザー" : owner.getUsername();
    }
}
