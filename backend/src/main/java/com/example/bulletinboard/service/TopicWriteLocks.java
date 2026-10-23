package com.example.bulletinboard.service;

import com.example.bulletinboard.model.Topic;
import com.example.bulletinboard.exception.TopicNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

/** All writers lock the Topic first; cleanup locks its lease before entering this order. */
final class TopicWriteLocks {
    private TopicWriteLocks() { }
    static Topic lock(EntityManager em, Long id, boolean requirePublic) {
        Topic topic = em.find(Topic.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (topic == null) throw new TopicNotFoundException("このお題は存在しないか、削除されています。");
        // A controller/admin Service may already have loaded the entity before waiting.
        em.refresh(topic, LockModeType.PESSIMISTIC_WRITE);
        if (requirePublic && topic.getDeletedAt() != null)
            throw new TopicNotFoundException("このお題は存在しないか、削除されています。");
        return topic;
    }
}
