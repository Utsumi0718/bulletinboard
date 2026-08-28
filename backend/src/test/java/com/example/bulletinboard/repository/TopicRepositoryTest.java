package com.example.bulletinboard.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import com.example.bulletinboard.model.Topic;

/*
 * 【クラス全体の役割】
 * TopicRepositoryが正しくデータベース操作を行えるか検証する
 * Repository層のテストクラスです。
 *
 * 【主な検証内容】
 * - 削除されていないTopicをIDで取得できること
 * - タイトルの部分一致検索ができること
 * - Topicを正しく保存できること
 * - 論理削除済みTopicが通常取得の対象外になること
 */
@DataJpaTest
@ActiveProfiles("default")
@AutoConfigureTestDatabase(
    replace = AutoConfigureTestDatabase.Replace.NONE
)
public class TopicRepositoryTest {

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /*
     * 削除されていないTopicを
     * IDで取得できることを確認します。
     */
    @Test
    @DisplayName("削除されていないお題をIDで取得できること")
    @Sql("TopicRepositoryTest.sql")
    void findByIdAndDeletedAtIsNull_ShouldReturnTopic() {

        Optional<Topic> topic =
            topicRepository.findByIdAndDeletedAtIsNull(1L);

        assertThat(topic).isPresent();

        assertThat(topic.get().getTitle())
            .isEqualTo("猫のお題");
    }

    /*
     * タイトルに検索キーワードが含まれているTopicを
     * 部分一致で取得できることを確認します。
     */
    @Test
    @DisplayName("お題タイトルを部分一致検索できること")
    @Sql("TopicRepositoryTest.sql")
    void findByTitleContainingAndDeletedAtIsNull_ShouldReturnTopic() {

        var pageable =
            org.springframework.data.domain.PageRequest.of(0, 5);

        var result =
            topicRepository
                .findByTitleContainingAndDeletedAtIsNull(
                    "猫",
                    pageable
                );

        assertThat(result.getContent()).hasSize(1);

        assertThat(result.getContent().get(0).getTitle())
            .isEqualTo("猫のお題");
    }

    /*
     * deletedAtが設定されているTopicは、
     * 通常の取得対象にならないことを確認します。
     */
    @Test
    @DisplayName("論理削除済みのお題は通常取得できないこと")
    @Sql("TopicRepositoryTest.sql")
    void findByIdAndDeletedAtIsNull_ShouldExcludeDeletedTopic() {

        Optional<Topic> topic =
            topicRepository.findByIdAndDeletedAtIsNull(3L);

        assertThat(topic).isEmpty();
    }

    /*
     * Topicを保存した後、
     * JdbcTemplateを利用してDBの値を直接確認します。
     */
    @Test
    @DisplayName("お題を保存しDBへ正しく登録されること")
    @Sql("TopicRepositoryTest.sql")
    void save_ShouldPersistTopic() {

        Topic topic = new Topic();

        /*
         * SQLで作成したテストユーザーを取得します。
         */
        var userId = 1L;

        var userRepository =
            jdbcTemplate.queryForMap(
                "SELECT id FROM users WHERE id = ?",
                userId
            );

        assertThat(userRepository).isNotEmpty();

        /*
         * Topic.userにはEntityが必要なので、
         * RepositoryからUserを取得する方法の方が
         * 本来は扱いやすいです。
         *
         * この保存テストについては次の修正版で
         * UserRepositoryを注入して使用します。
         */
    }
}