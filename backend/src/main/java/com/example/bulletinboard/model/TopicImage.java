package com.example.bulletinboard.model;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** アップロードした原本。差し替えは別IDで保存し、元の画像を上書きしない。 */
@Entity
@Table(name = "topic_images")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TopicImage {
    @Id
    @Column(length = 36, updatable = false)
    private String id;

    @Column(name = "owner_user_id", nullable = false, updatable = false)
    private Long ownerUserId;

    @Column(name = "content_type", nullable = false, length = 30, updatable = false)
    private String contentType;

    @Column(name = "byte_size", nullable = false, updatable = false)
    private int byteSize;

    @Lob
    @Column(nullable = false, columnDefinition = "MEDIUMBLOB", updatable = false)
    private byte[] content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public TopicImage(Long ownerUserId, String contentType, byte[] content) {
        this.id = UUID.randomUUID().toString();
        this.ownerUserId = ownerUserId;
        this.contentType = contentType;
        this.content = content.clone();
        this.byteSize = content.length;
        this.createdAt = LocalDateTime.now();
    }

    public byte[] getContent() {
        return content.clone();
    }
}
