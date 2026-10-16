package com.example.bulletinboard.service;

import java.util.Objects;
import com.example.bulletinboard.model.AccountStatus;
import com.example.bulletinboard.model.Answer;
import com.example.bulletinboard.model.Profile;
import com.example.bulletinboard.model.Topic;
import com.example.bulletinboard.model.User;

/** メニュー表示の目安。通報済み判定をせず、受付APIの再検証を置き換えない。 */
public final class ReportEligibility {
    private ReportEligibility() { }
    private static boolean otherOwner(User owner, User viewer) {
        return owner != null && owner.getId() != null && viewer != null && viewer.getId() != null
                && viewer.getAccountStatus() == AccountStatus.ACTIVE
                && !Objects.equals(owner.getId(), viewer.getId());
    }
    public static boolean canReport(Topic topic, User viewer) {
        return topic != null && topic.getDeletedAt() == null && otherOwner(topic.getUser(), viewer);
    }
    public static boolean canReport(Answer answer, User viewer) {
        return answer != null && answer.getDeletedAt() == null && answer.getTopic() != null
                && answer.getTopic().getDeletedAt() == null && otherOwner(answer.getUser(), viewer);
    }
    public static boolean canReport(Profile profile, User viewer) {
        return profile != null && otherOwner(profile.getUser(), viewer)
                && profile.getUser().getAccountStatus() != AccountStatus.WITHDRAWN;
    }
}
