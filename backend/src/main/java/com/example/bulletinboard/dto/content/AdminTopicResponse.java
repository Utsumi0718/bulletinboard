package com.example.bulletinboard.dto.content;

import java.time.LocalDateTime;

import com.example.bulletinboard.model.Topic;

/** 論理削除済みも含む管理者用のお題情報。 */
public record AdminTopicResponse(Long id, Long ownerUserId, String ownerUsername,
        String title, String question, String image, AdminContentVisibility visibility,
        LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime deletedAt) {
    public static AdminTopicResponse from(Topic topic) {
        return new AdminTopicResponse(topic.getId(), topic.getUser().getId(), topic.getUser().getUsername(),
                topic.getTitle(), topic.getQuestion(), topic.getImage(),
                topic.getDeletedAt() == null ? AdminContentVisibility.PUBLIC : AdminContentVisibility.DELETED,
                topic.getCreatedAt(), topic.getUpdatedAt(), topic.getDeletedAt());
    }
}
