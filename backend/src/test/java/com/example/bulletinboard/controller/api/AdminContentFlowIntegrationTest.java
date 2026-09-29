package com.example.bulletinboard.controller.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.bulletinboard.exception.ForbiddenOperationException;
import com.example.bulletinboard.exception.TopicNotFoundException;
import com.example.bulletinboard.exception.handler.GlobalExceptionHandler;
import com.example.bulletinboard.model.AccountStatus;
import com.example.bulletinboard.model.Answer;
import com.example.bulletinboard.model.RankingCheckpoint;
import com.example.bulletinboard.model.RankingJudgment;
import com.example.bulletinboard.model.RankingResult;
import com.example.bulletinboard.model.Topic;
import com.example.bulletinboard.model.TopicImage;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.model.WinnerAchievement;
import com.example.bulletinboard.repository.AdminOperationLogRepository;
import com.example.bulletinboard.repository.AnswerRepository;
import com.example.bulletinboard.repository.RankingJudgmentRepository;
import com.example.bulletinboard.repository.RankingResultRepository;
import com.example.bulletinboard.repository.TopicImageRepository;
import com.example.bulletinboard.repository.TopicRepository;
import com.example.bulletinboard.repository.UserRepository;
import com.example.bulletinboard.repository.WinnerAchievementRepository;
import com.example.bulletinboard.security.SecurityConfig;
import com.example.bulletinboard.service.AdminContentService;
import com.example.bulletinboard.service.AnswerService;
import com.example.bulletinboard.service.CustomUserDetailsService;
import com.example.bulletinboard.service.LikeService;
import com.example.bulletinboard.service.RankingService;
import com.example.bulletinboard.service.TopicImageService;
import com.example.bulletinboard.service.TopicService;

/** E-3の実Controller・Service・Repository・Securityを専用H2で確認する。 */
@SpringBootTest(classes = AdminContentFlowIntegrationTest.FlowConfiguration.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-content-flow;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=false", "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminContentFlowIntegrationTest {
    private static final String ADMIN = "admin@example.com";
    private static final String OWNER = "owner@example.com";

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = User.class)
    @EnableJpaRepositories(basePackageClasses = UserRepository.class)
    @Import({AdminContentApiController.class, AdminContentService.class, TopicService.class,
            AnswerService.class, RankingService.class, CustomUserDetailsService.class,
            SecurityConfig.class, GlobalExceptionHandler.class})
    static class FlowConfiguration { }

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TopicRepository topics;
    @Autowired AnswerRepository answers;
    @Autowired TopicImageRepository images;
    @Autowired AdminOperationLogRepository logs;
    @Autowired RankingJudgmentRepository judgments;
    @Autowired RankingResultRepository results;
    @Autowired WinnerAchievementRepository achievements;
    @Autowired TopicService topicService;
    @Autowired AnswerService answerService;
    @Autowired RankingService rankingService;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean TopicImageService topicImages;
    @MockitoBean LikeService likes;

    @BeforeEach
    void setup() {
        logs.deleteAll();
        achievements.deleteAll();
        results.deleteAll();
        judgments.deleteAll();
        answers.deleteAll();
        topics.deleteAll();
        images.deleteAll();
        users.deleteAll();
        users.saveAndFlush(account("admin", ADMIN, "ROLE_ADMIN"));
        users.saveAndFlush(account("owner", OWNER, "ROLE_USER"));
    }

    @Test
    void topicListSeparatesPublicAndDeletedWithPaging() throws Exception {
        Topic publicTopic = topic();
        Topic deletedTopic = topic();
        deletedTopic.setDeletedAt(LocalDateTime.now());
        topics.saveAndFlush(deletedTopic);
        mvc.perform(get("/api/admin/topics").with(user(ADMIN).roles("ADMIN"))
                .param("visibility", "PUBLIC").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(publicTopic.getId()));
        mvc.perform(get("/api/admin/topics").with(user(ADMIN).roles("ADMIN"))
                .param("visibility", "DELETED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(deletedTopic.getId()));
        mvc.perform(get("/api/admin/topics").with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void deletedTopicDetailKeepsContentAndOwner() throws Exception {
        Topic topic = topic();
        topic.setDeletedAt(LocalDateTime.now());
        topics.saveAndFlush(topic);
        mvc.perform(get("/api/admin/topics/{id}", topic.getId()).with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("topic-title"))
                .andExpect(jsonPath("$.question").value("topic-question"))
                .andExpect(jsonPath("$.ownerUserId").value(owner().getId()))
                .andExpect(jsonPath("$.visibility").value("DELETED"));
        assertThatThrownBy(() -> topicService.getById(topic.getId())).isInstanceOf(TopicNotFoundException.class);
    }

    @Test
    void answerListTreatsDeletedParentAsNotPublic() throws Exception {
        Topic parent = topic();
        Answer first = answer(parent);
        Topic deletedParent = topic();
        deletedParent.setDeletedAt(LocalDateTime.now());
        topics.saveAndFlush(deletedParent);
        Answer hidden = answer(deletedParent);
        mvc.perform(get("/api/admin/answers").with(user(ADMIN).roles("ADMIN"))
                .param("visibility", "PUBLIC"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(first.getId()));
        mvc.perform(get("/api/admin/answers").with(user(ADMIN).roles("ADMIN"))
                .param("visibility", "DELETED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(hidden.getId()))
                .andExpect(jsonPath("$.content[0].parentDeleted").value(true));
    }

    @Test
    void deletedAnswerDetailKeepsContent() throws Exception {
        Answer answer = answer(topic());
        answer.setDeletedAt(LocalDateTime.now());
        answers.saveAndFlush(answer);
        mvc.perform(get("/api/admin/answers/{id}", answer.getId()).with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").value("answer-content"))
                .andExpect(jsonPath("$.visibility").value("DELETED"))
                .andExpect(jsonPath("$.parentDeleted").value(false));
        assertThat(answerService.getAnswersByTopicId(answer.getTopic().getId())).isEmpty();
    }

    @Test
    void adminCanReadManagedImageOfDeletedTopic() throws Exception {
        TopicImage image = images.saveAndFlush(new TopicImage(owner().getId(), "image/png", new byte[] {1, 2, 3}));
        Topic topic = topic();
        topic.setImage(TopicImageService.URL_PREFIX + image.getId());
        topic.setDeletedAt(LocalDateTime.now());
        topics.saveAndFlush(topic);
        byte[] bytes = mvc.perform(get("/api/admin/topics/{id}/image", topic.getId())
                .with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(bytes).containsExactly(1, 2, 3);
    }

    @Test
    void unmanagedImageReferenceCannotBeReadAsManagedImage() throws Exception {
        Topic topic = topic();
        mvc.perform(get("/api/admin/topics/{id}/image", topic.getId())
                .with(user(ADMIN).roles("ADMIN"))).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @CsvSource({"topics", "answers"})
    void missingDetailReturns404(String kind) throws Exception {
        mvc.perform(get("/api/admin/{kind}/999999", kind).with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @CsvSource({"topics,-1,20", "topics,0,0", "topics,0,101", "answers,-1,20", "answers,0,0", "answers,0,101"})
    void invalidPagingReturns400(String kind, int page, int size) throws Exception {
        mvc.perform(get("/api/admin/{kind}", kind).with(user(ADMIN).roles("ADMIN"))
                .param("page", String.valueOf(page)).param("size", String.valueOf(size)))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @CsvSource({"topics", "answers"})
    void invalidVisibilityReturns400(String kind) throws Exception {
        mvc.perform(get("/api/admin/{kind}", kind).with(user(ADMIN).roles("ADMIN"))
                .param("visibility", "INVALID"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void topicDeleteIsLogicalAndWritesHistory() throws Exception {
        Topic topic = topic();
        mvc.perform(delete("/api/admin/topics/{id}", topic.getId())
                .with(user(ADMIN).roles("ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(topics.findById(topic.getId()).orElseThrow().getDeletedAt()).isNotNull();
        assertThatThrownBy(() -> topicService.getById(topic.getId())).isInstanceOf(TopicNotFoundException.class);
        var history = logs.findByTargetTypeAndTargetIdOrderByCreatedAtAscIdAsc("TOPIC", topic.getId());
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().getAdminUser().getId()).isEqualTo(users.findByEmail(ADMIN).orElseThrow().getId());
        assertThat(history.getFirst().getBeforeStatus()).isEqualTo("PUBLIC");
        assertThat(history.getFirst().getAfterStatus()).isEqualTo("DELETED");
        assertThat(history.getFirst().getResult()).isEqualTo("SUCCESS");
    }

    @Test
    void answerDeleteIsLogicalAndWritesHistory() throws Exception {
        Answer answer = answer(topic());
        mvc.perform(delete("/api/admin/answers/{id}", answer.getId())
                .with(user(ADMIN).roles("ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(answers.findById(answer.getId()).orElseThrow().getDeletedAt()).isNotNull();
        assertThat(answerService.getAnswersByTopicId(answer.getTopic().getId())).isEmpty();
        var history = logs.findByTargetTypeAndTargetIdOrderByCreatedAtAscIdAsc("ANSWER", answer.getId());
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().getBeforeStatus()).isEqualTo("PUBLIC");
        assertThat(history.getFirst().getAfterStatus()).isEqualTo("DELETED");
    }

    @Test
    void deletingAnswerOfDeletedParentRecordsPreviousVisibility() throws Exception {
        Topic parent = topic();
        Answer answer = answer(parent);
        parent.setDeletedAt(LocalDateTime.now());
        topics.saveAndFlush(parent);
        mvc.perform(delete("/api/admin/answers/{id}", answer.getId())
                .with(user(ADMIN).roles("ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(logs.findByTargetTypeAndTargetIdOrderByCreatedAtAscIdAsc("ANSWER", answer.getId())
                .getFirst().getBeforeStatus()).isEqualTo("PARENT_DELETED");
    }

    @ParameterizedTest
    @CsvSource({"topics", "answers"})
    void repeatedDeletionReturns404AndKeepsSingleHistory(String kind) throws Exception {
        Long id = kind.equals("topics") ? topic().getId() : answer(topic()).getId();
        mvc.perform(delete("/api/admin/{kind}/{id}", kind, id).with(user(ADMIN).roles("ADMIN"))
                .with(csrf())).andExpect(status().isNoContent());
        mvc.perform(delete("/api/admin/{kind}/{id}", kind, id).with(user(ADMIN).roles("ADMIN"))
                .with(csrf())).andExpect(status().isNotFound());
        assertThat(logs.findByTargetTypeAndTargetIdOrderByCreatedAtAscIdAsc(
                kind.equals("topics") ? "TOPIC" : "ANSWER", id)).hasSize(1);
    }

    @Test
    void ordinaryUserAndAnonymousCannotManageContent() throws Exception {
        Topic topic = topic();
        mvc.perform(get("/api/admin/topics")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/topics").with(user(OWNER).roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/topics/{id}", topic.getId())
                .with(user(OWNER).roles("USER")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/answers/{id}", answer(topic).getId())
                .with(user(OWNER).roles("USER")).with(csrf())).andExpect(status().isForbidden());
        assertThat(logs.count()).isZero();
    }

    @Test
    void missingCsrfAndInactiveAdminCannotDelete() throws Exception {
        Topic topic = topic();
        mvc.perform(delete("/api/admin/topics/{id}", topic.getId())
                .with(user(ADMIN).roles("ADMIN"))).andExpect(status().isForbidden());
        User admin = users.findByEmail(ADMIN).orElseThrow();
        admin.setAccountStatus(AccountStatus.FROZEN);
        users.saveAndFlush(admin);
        mvc.perform(delete("/api/admin/topics/{id}", topic.getId())
                .with(user(ADMIN).roles("ADMIN")).with(csrf())).andExpect(status().isForbidden());
        assertThat(topics.findById(topic.getId()).orElseThrow().getDeletedAt()).isNull();
    }

    @Test
    void missingAdminRecordStopsDeletion() throws Exception {
        Topic topic = topic();
        mvc.perform(delete("/api/admin/topics/{id}", topic.getId())
                .with(user("missing@example.com").roles("ADMIN")).with(csrf()))
                .andExpect(status().isInternalServerError());
        assertThat(topics.findById(topic.getId()).orElseThrow().getDeletedAt()).isNull();
        assertThat(logs.count()).isZero();
    }

    @Test
    void adminStillCannotEditSomeoneElsesOrdinaryPost() {
        Topic topic = topic();
        Answer answer = answer(topic);
        assertThatThrownBy(() -> topicService.updateTopic(topic.getId(), ADMIN,
                "changed", topic.getImage(), "changed"))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> answerService.updateAnswer(answer.getId(), ADMIN, "changed"))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void deletionExcludesFutureRankingButKeepsRecordedAchievement() throws Exception {
        Topic topic = topic();
        Answer answer = answer(topic);
        RankingJudgment judgment = new RankingJudgment();
        judgment.setTopic(topic);
        judgment.setCheckpoint(RankingCheckpoint.DAY_7);
        judgment.setJudgedAt(LocalDateTime.now());
        judgments.saveAndFlush(judgment);
        RankingResult result = new RankingResult();
        result.setRankingJudgment(judgment);
        result.setAnswer(answer);
        result.setLikeCount(2);
        results.saveAndFlush(result);
        WinnerAchievement achievement = new WinnerAchievement();
        achievement.setUser(owner());
        achievement.setTopic(topic);
        achievement.setAnswer(answer);
        achievement.setLikeCount(2);
        achievement.setAchievedAt(LocalDateTime.now());
        achievements.saveAndFlush(achievement);
        mvc.perform(delete("/api/admin/topics/{id}", topic.getId())
                .with(user(ADMIN).roles("ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());
        rankingService.judgeRanking(topic.getId(), RankingCheckpoint.DAY_14);
        assertThat(judgments.count()).isEqualTo(1);
        assertThat(results.count()).isEqualTo(1);
        assertThat(achievements.count()).isEqualTo(1);
    }

    @Test
    void deletedAnswerIsExcludedFromFutureRanking() throws Exception {
        Topic topic = topic();
        Answer answer = answer(topic);
        mvc.perform(delete("/api/admin/answers/{id}", answer.getId())
                .with(user(ADMIN).roles("ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());
        rankingService.judgeRanking(topic.getId(), RankingCheckpoint.DAY_7);
        assertThat(judgments.count()).isEqualTo(1);
        assertThat(results.count()).isZero();
    }

    @Test
    void historyFailureRollsBackTopicDeletion() throws Exception {
        Topic topic = topic();
        jdbc.execute("ALTER TABLE admin_operation_logs ADD CONSTRAINT ck_content_log_fail "
                + "CHECK (target_type <> 'TOPIC')");
        try {
            mvc.perform(delete("/api/admin/topics/{id}", topic.getId())
                    .with(user(ADMIN).roles("ADMIN")).with(csrf()))
                    .andExpect(status().isInternalServerError());
            assertThat(topics.findById(topic.getId()).orElseThrow().getDeletedAt()).isNull();
        } finally {
            jdbc.execute("ALTER TABLE admin_operation_logs DROP CONSTRAINT ck_content_log_fail");
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

    private Topic topic() {
        Topic topic = new Topic();
        topic.setUser(owner());
        topic.setTitle("topic-title");
        topic.setQuestion("topic-question");
        topic.setImage("https://example.invalid/old-image.png");
        return topics.saveAndFlush(topic);
    }

    private Answer answer(Topic topic) {
        Answer answer = new Answer();
        answer.setTopic(topic);
        answer.setUser(owner());
        answer.setContent("answer-content");
        return answers.saveAndFlush(answer);
    }
}
