package com.example.bulletinboard.dto.user;

import java.time.LocalDateTime;

import com.example.bulletinboard.model.Answer;
import com.example.bulletinboard.model.Topic;

/** 管理者向け投稿履歴。論理削除済みも含む。 */
public record AdminUserActivityResponse(Long id, String content, Long topicId,
        LocalDateTime createdAt, LocalDateTime deletedAt) {
    public static AdminUserActivityResponse from(Topic topic) {
        return new AdminUserActivityResponse(topic.getId(), topic.getTitle(), null,
                topic.getCreatedAt(), topic.getDeletedAt());
    }

    public static AdminUserActivityResponse from(Answer answer) {
        return new AdminUserActivityResponse(answer.getId(), answer.getContent(), answer.getTopic().getId(),
                answer.getCreatedAt(), answer.getDeletedAt());
    }
}
