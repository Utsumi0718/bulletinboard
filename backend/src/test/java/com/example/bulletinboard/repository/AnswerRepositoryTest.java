package com.example.bulletinboard.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import com.example.bulletinboard.model.Answer;

/*
 * 【クラスの役割】
 * AnswerRepositoryが正しくデータベース操作を行えるか検証する
 * Repository層のテストクラスです。
 *
 * 【主な検証内容】
 * - Topicに紐づく削除されていない回答を取得できること
 * - IDから削除されていない回答を取得できること
 * - 論理削除済み回答が通常取得から除外されること
 */
@DataJpaTest
@ActiveProfiles("default")
@AutoConfigureTestDatabase(
    replace = AutoConfigureTestDatabase.Replace.NONE
)
class AnswerRepositoryTest {

    @Autowired
    private AnswerRepository answerRepository;

    @Test
    @DisplayName("指定したTopicに紐づく削除されていない回答を取得できること")
    @Sql("AnswerRepositoryTest.sql")
    void findByTopicIdAndDeletedAtIsNull_ShouldReturnAnswers() {

        List<Answer> answers =
            answerRepository
                .findByTopicIdAndDeletedAtIsNull(1L);

        assertThat(answers).hasSize(2);

        assertThat(answers)
            .extracting(Answer::getContent)
            .containsExactlyInAnyOrder(
                "テスト回答1",
                "テスト回答2"
            );
    }

    @Test
    @DisplayName("削除されていない回答をIDで取得できること")
    @Sql("AnswerRepositoryTest.sql")
    void findByIdAndDeletedAtIsNull_ShouldReturnAnswer() {

        Optional<Answer> answer =
            answerRepository
                .findByIdAndDeletedAtIsNull(1L);

        assertThat(answer).isPresent();

        assertThat(answer.get().getContent())
            .isEqualTo("テスト回答1");
    }

    @Test
    @DisplayName("論理削除済みの回答は通常取得できないこと")
    @Sql("AnswerRepositoryTest.sql")
    void findByIdAndDeletedAtIsNull_ShouldExcludeDeletedAnswer() {

        Optional<Answer> answer =
            answerRepository
                .findByIdAndDeletedAtIsNull(3L);

        assertThat(answer).isEmpty();
    }
}