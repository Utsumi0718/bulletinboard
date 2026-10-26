package com.example.bulletinboard.controller.api;

import static com.example.bulletinboard.support.TestActors.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.example.bulletinboard.exception.handler.GlobalExceptionHandler;
import com.example.bulletinboard.model.RankingCheckpoint;
import com.example.bulletinboard.model.Topic;
import com.example.bulletinboard.model.TopicImage;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.TopicImageRepository;
import com.example.bulletinboard.repository.TopicRepository;
import com.example.bulletinboard.repository.UserRepository;
import com.example.bulletinboard.security.SecurityConfig;
import com.example.bulletinboard.service.AdminContentService;
import com.example.bulletinboard.service.AnswerService;
import com.example.bulletinboard.service.CustomUserDetailsService;
import com.example.bulletinboard.service.LikeService;
import com.example.bulletinboard.service.RankingService;
import com.example.bulletinboard.service.TopicImageService;
import com.example.bulletinboard.service.TopicService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 回答編集の撤去と、投稿・削除・再投稿の契約を専用H2で確認する。
 * テスト全体をトランザクションで包まず、各API／Serviceの確定後にDBを読み直す。
 * 初期データ投入やランキング・クリーンアップの定期処理は読み込まない。
 */
@SpringBootTest(classes = AnswerLifecycleIntegrationTest.Config.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:answer-lifecycle;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=false", "spring.sql.init.mode=never",
        "topic.cleanup.enabled=false", "contact.retention.enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AnswerLifecycleIntegrationTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = User.class)
    @EnableJpaRepositories(basePackageClasses = UserRepository.class)
    @Import({AnswerApiController.class, LikeApiController.class, AdminContentApiController.class,
            AnswerService.class, LikeService.class, TopicService.class, TopicImageService.class,
            AdminContentService.class, RankingService.class, CustomUserDetailsService.class,
            SecurityConfig.class, GlobalExceptionHandler.class})
    static class Config { }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired TopicRepository topics;
    @Autowired TopicImageRepository images;
    @Autowired RankingService ranking;
    @Autowired RequestMappingHandlerMapping mappings;
    User owner;
    User other;
    User admin;
    Topic topic;

    @BeforeEach
    void setUp() {
        for (String table : List.of("admin_operation_logs", "winner_achievements", "ranking_results",
                "ranking_judgments", "likes", "answers", "topics", "topic_images", "users")) {
            jdbc.update("DELETE FROM " + table);
        }
        owner = account("answer-owner", "ROLE_USER");
        other = account("other-user", "ROLE_USER");
        admin = account("administrator", "ROLE_ADMIN");
        var image = images.saveAndFlush(new TopicImage(other.getId(), "image/png", new byte[]{1, 2, 3}));
        topic = new Topic();
        topic.setUser(other);
        topic.setTitle("test topic");
        topic.setQuestion("test question");
        topic.setImage(TopicImageService.URL_PREFIX + image.getId());
        topic = topics.saveAndFlush(topic);
    }

    @Test
    void answerPutHasNoControllerMapping() {
        assertThat(mappings.getHandlerMethods().keySet()).noneMatch(mapping ->
                mapping.getPatternValues().contains("/api/answers/{id}")
                && (mapping.getMethodsCondition().getMethods().isEmpty()
                    || mapping.getMethodsCondition().getMethods().contains(RequestMethod.PUT)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void ownerAndAdminCannotPutEvenWithValidAuthenticationAndCsrf(boolean asAdmin) throws Exception {
        long id = postAnswer("original answer");
        User actor = asAdmin ? admin : owner;
        mvc.perform(put("/api/answers/{id}", id)
                .with(user(actor.getId().toString()).roles(asAdmin ? "ADMIN" : "USER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"changed\"}"))
                .andExpect(status().isMethodNotAllowed());
        assertThat(jdbc.queryForObject("SELECT content FROM answers WHERE id=?", String.class, id))
                .isEqualTo("original answer");
        assertThat(jdbc.queryForObject("SELECT deleted_at FROM answers WHERE id=?", LocalDateTime.class, id))
                .isNull();
    }

    @Test
    void deleteLikedWinnerAndRepostKeepsOriginalRecordsAndFirstAchievement() throws Exception {
        long original = postAnswer("original answer");
        // 時間待ちをせず、新旧の投稿日時を区別できる状態を作る。
        LocalDateTime earlier = LocalDateTime.now().minusDays(1).withNano(0);
        jdbc.update("UPDATE answers SET created_at=? WHERE id=?", earlier, original);
        like(original);
        ranking.judgeRanking(topic.getId(), RankingCheckpoint.DAY_7);
        var firstAchievement = jdbc.queryForMap("SELECT id,answer_id,like_count,achieved_at FROM winner_achievements WHERE user_id=? AND topic_id=?",
                owner.getId(), topic.getId());
        var firstResult = jdbc.queryForMap("SELECT id,answer_id,like_count FROM ranking_results WHERE answer_id=?", original);

        mvc.perform(delete("/api/answers/{id}", original).with(user(owner.getId().toString())).with(csrf()))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(jdbc.queryForObject("SELECT deleted_at FROM answers WHERE id=?", LocalDateTime.class, original)).isNotNull();
        assertThat(count("SELECT COUNT(*) FROM likes WHERE answer_id=?", original)).isEqualTo(1);
        mvc.perform(get("/api/topics/{id}/answers", topic.getId()).with(user(owner.getId().toString())))
                .andExpect(status().isOk()).andExpect(content().json("[]"));

        long replacement = postAnswer("replacement answer");
        assertThat(replacement).isNotEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT created_at FROM answers WHERE id=?", LocalDateTime.class, replacement))
                .isAfter(earlier);
        assertThat(count("SELECT COUNT(*) FROM likes WHERE answer_id=?", replacement)).isZero();
        like(replacement);
        ranking.judgeRanking(topic.getId(), RankingCheckpoint.DAY_14);
        ranking.judgeRanking(topic.getId(), RankingCheckpoint.DAY_21);
        assertThat(count("SELECT COUNT(*) FROM ranking_results WHERE answer_id=?", original)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM ranking_results WHERE answer_id=?", replacement)).isEqualTo(2);
        assertThat(jdbc.queryForMap("SELECT id,answer_id,like_count FROM ranking_results WHERE answer_id=?", original))
                .isEqualTo(firstResult);
        assertThat(count("SELECT COUNT(*) FROM winner_achievements WHERE user_id=? AND topic_id=?", owner.getId(), topic.getId()))
                .isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT id,answer_id,like_count,achieved_at FROM winner_achievements WHERE user_id=? AND topic_id=?",
                owner.getId(), topic.getId())).isEqualTo(firstAchievement);
    }

    @Test
    void deletedParentRejectsPostingButStillAllowsDirectOwnerDeletion() throws Exception {
        long id = postAnswer("original answer");
        jdbc.update("UPDATE topics SET deleted_at=? WHERE id=?", LocalDateTime.now(), topic.getId());
        mvc.perform(post("/api/topics/{id}/answers", topic.getId())
                .with(user(owner.getId().toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"new answer\"}"))
                .andExpect(status().isNotFound());
        assertThat(count("SELECT COUNT(*) FROM answers WHERE topic_id=?", topic.getId())).isEqualTo(1);
        mvc.perform(delete("/api/answers/{id}", id).with(user(owner.getId().toString())).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT deleted_at FROM answers WHERE id=?", LocalDateTime.class, id)).isNotNull();
    }

    @Test
    void anotherUserCannotDeleteAnAnswer() throws Exception {
        long id = postAnswer("original answer");
        mvc.perform(delete("/api/answers/{id}", id).with(user(other.getId().toString())).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT deleted_at FROM answers WHERE id=?", LocalDateTime.class, id)).isNull();
    }

    @Test
    void administratorCanDeleteThroughManagementApiWithHistory() throws Exception {
        long id = postAnswer("original answer");
        mvc.perform(delete("/api/admin/answers/{id}", id)
                .with(user(admin.getId().toString()).roles("ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT deleted_at FROM answers WHERE id=?", LocalDateTime.class, id)).isNotNull();
        assertThat(count("SELECT COUNT(*) FROM admin_operation_logs WHERE target_type='ANSWER' AND target_id=? AND admin_user_id=? AND action='DELETE' AND result='SUCCESS'",
                id, admin.getId())).isEqualTo(1);
    }

    private User account(String name, String role) {
        var user = new User();
        user.setUsername(name);
        user.setEmail(name + "@example.test");
        user.setPassword("test-only-not-a-credential");
        user.setRole(role);
        return users.saveAndFlush(user);
    }

    private long postAnswer(String body) throws Exception {
        var result = mvc.perform(post("/api/topics/{id}/answers", topic.getId())
                .with(user(owner.getId().toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of("content", body))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.content").value(body))
                .andExpect(jsonPath("$.liked").value(false)).andExpect(jsonPath("$.likeCount").value(0))
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void like(long id) throws Exception {
        mvc.perform(post("/api/answers/{id}/like", id).with(user(other.getId().toString())).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(1));
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
}
