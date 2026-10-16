package com.example.bulletinboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import com.example.bulletinboard.model.*;
import com.example.bulletinboard.dto.answer.AnswerResponse;
import com.example.bulletinboard.dto.topic.TopicResponse;
import com.example.bulletinboard.dto.profile.ProfileResponse;

class ReportEligibilityTest {
    @ParameterizedTest
    @CsvSource({"TOPIC,ANONYMOUS,false", "TOPIC,SELF,false", "TOPIC,OTHER,true", "TOPIC,INACTIVE,false",
        "ANSWER,ANONYMOUS,false", "ANSWER,SELF,false", "ANSWER,OTHER,true", "ANSWER,INACTIVE,false",
        "PROFILE,ANONYMOUS,false", "PROFILE,SELF,false", "PROFILE,OTHER,true", "PROFILE,INACTIVE,false"})
    void mapsViewerEligibilityWithoutExposingUserIds(String type, String viewerKind, boolean expected) {
        User owner = user(1L, AccountStatus.ACTIVE);
        User viewer = switch (viewerKind) {
            case "ANONYMOUS" -> null;
            case "SELF" -> owner;
            case "INACTIVE" -> user(2L, AccountStatus.FROZEN);
            default -> user(2L, AccountStatus.ACTIVE);
        };
        Topic topic = topic(owner);
        Answer answer = answer(owner, topic);
        Profile profile = profile(owner);
        boolean actual = switch (type) {
            case "TOPIC" -> TopicResponse.from(topic, viewer).isCanReport();
            case "ANSWER" -> AnswerResponse.from(answer, true, 3, viewer).isCanReport();
            default -> ProfileResponse.from(profile, viewer).canReport();
        };
        assertThat(actual).isEqualTo(expected);
    }

    @ParameterizedTest @ValueSource(strings = {"TOPIC", "ANSWER", "PROFILE"})
    void unavailableTargetsCannotBeReported(String type) {
        User owner = user(1L, AccountStatus.ACTIVE), viewer = user(2L, AccountStatus.ACTIVE);
        Topic topic = topic(owner);
        Answer answer = answer(owner, topic);
        Profile profile = profile(owner);
        topic.setDeletedAt(java.time.LocalDateTime.now());
        owner.setAccountStatus(AccountStatus.WITHDRAWN);
        assertThat(switch (type) {
            case "TOPIC" -> ReportEligibility.canReport(topic, viewer);
            case "ANSWER" -> ReportEligibility.canReport(answer, viewer);
            default -> ReportEligibility.canReport(profile, viewer);
        }).isFalse();
    }

    @Test void topicOwnerMayReportAnotherUsersAnswer() {
        User topicOwner = user(1L, AccountStatus.ACTIVE);
        assertThat(ReportEligibility.canReport(answer(user(2L, AccountStatus.ACTIVE), topic(topicOwner)), topicOwner)).isTrue();
    }

    @Test void deletedAnswerCannotBeReportedEvenWhenParentIsPublic() {
        Answer answer = answer(user(1L, AccountStatus.ACTIVE), topic(user(2L, AccountStatus.ACTIVE)));
        answer.setDeletedAt(java.time.LocalDateTime.now());
        assertThat(ReportEligibility.canReport(answer, user(3L, AccountStatus.ACTIVE))).isFalse();
    }

    @ParameterizedTest @ValueSource(strings = {"TOPIC", "ANSWER", "PROFILE"})
    void inactiveOwnersRemainReportableAsSpecified(String type) {
        User owner = user(1L, type.equals("PROFILE") ? AccountStatus.FROZEN : AccountStatus.WITHDRAWN);
        User viewer = user(2L, AccountStatus.ACTIVE);
        Topic topic = topic(owner);
        assertThat(switch (type) {
            case "TOPIC" -> ReportEligibility.canReport(topic, viewer);
            case "ANSWER" -> ReportEligibility.canReport(answer(owner, topic), viewer);
            default -> ReportEligibility.canReport(profile(owner), viewer);
        }).isTrue();
    }

    private User user(Long id, AccountStatus state) {
        User user = new User(); user.setId(id); user.setUsername("name"); user.setAccountStatus(state); return user;
    }
    private Topic topic(User owner) { Topic t = new Topic(); t.setUser(owner); return t; }
    private Answer answer(User owner, Topic topic) { Answer a = new Answer(); a.setUser(owner); a.setTopic(topic); return a; }
    private Profile profile(User owner) { Profile p = new Profile(); p.setUser(owner); return p; }
}
