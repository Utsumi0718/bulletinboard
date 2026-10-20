package com.example.bulletinboard.dto.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.example.bulletinboard.dto.answer.AnswerResponse;
import com.example.bulletinboard.dto.profile.ProfileResponse;
import com.example.bulletinboard.dto.topic.TopicResponse;
import com.example.bulletinboard.model.AccountStatus;
import com.example.bulletinboard.model.Answer;
import com.example.bulletinboard.model.Profile;
import com.example.bulletinboard.model.Topic;
import com.example.bulletinboard.model.User;

class PublicDisplayNameTest {
    @ParameterizedTest
    @EnumSource(AccountStatus.class)
    void topicOverloadsUsePublicNameWithoutChangingUser(AccountStatus status) {
        User owner = owner(status);
        Topic topic = new Topic();
        topic.setUser(owner);
        assertThat(TopicResponse.from(topic).getUsername()).isEqualTo(expected(status));
        assertThat(TopicResponse.from(topic, null).getUsername()).isEqualTo(expected(status));
        assertThat(owner.getUsername()).isEqualTo("original-owner");
    }

    @ParameterizedTest
    @EnumSource(AccountStatus.class)
    void answerOverloadsPreserveLikeInformation(AccountStatus status) {
        User owner = owner(status);
        Answer answer = new Answer();
        answer.setUser(owner);
        answer.setTopic(new Topic());
        assertThat(AnswerResponse.from(answer).getUsername()).isEqualTo(expected(status));
        var response = AnswerResponse.from(answer, true, 3, null);
        assertThat(response.getUsername()).isEqualTo(expected(status));
        assertThat(response.isLiked()).isTrue();
        assertThat(response.getLikeCount()).isEqualTo(3);
        assertThat(owner.getUsername()).isEqualTo("original-owner");
    }

    @ParameterizedTest
    @EnumSource(AccountStatus.class)
    void profileMappingDoesNotExposeWithdrawnName(AccountStatus status) {
        User owner = owner(status);
        Profile profile = new Profile();
        profile.setUser(owner);
        assertThat(ProfileResponse.from(profile, null).username()).isEqualTo(expected(status));
        assertThat(owner.getUsername()).isEqualTo("original-owner");
    }

    private User owner(AccountStatus status) {
        User user = new User();
        user.setId(1L);
        user.setUsername("original-owner");
        user.setAccountStatus(status);
        return user;
    }

    private String expected(AccountStatus status) {
        return status == AccountStatus.WITHDRAWN ? "退会済みユーザー" : "original-owner";
    }
}
