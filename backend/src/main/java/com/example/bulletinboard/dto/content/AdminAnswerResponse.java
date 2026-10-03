package com.example.bulletinboard.dto.content;

import java.time.LocalDateTime;

import com.example.bulletinboard.model.Answer;

/** 回答自体と親Topicの削除状態を区別する管理者用情報。 */
public record AdminAnswerResponse(Long id, Long topicId, Long ownerUserId, String ownerUsername,
        String content, AdminContentVisibility visibility, boolean parentDeleted,
        LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime deletedAt) {
    public static AdminAnswerResponse from(Answer answer) {
        boolean parentDeleted = answer.getTopic().getDeletedAt() != null;
        return new AdminAnswerResponse(answer.getId(), answer.getTopic().getId(),
                answer.getUser().getId(), answer.getUser().getUsername(), answer.getContent(),
                answer.getDeletedAt() == null && !parentDeleted
                        ? AdminContentVisibility.PUBLIC : AdminContentVisibility.DELETED,
                parentDeleted, answer.getCreatedAt(), answer.getUpdatedAt(), answer.getDeletedAt());
    }
}
