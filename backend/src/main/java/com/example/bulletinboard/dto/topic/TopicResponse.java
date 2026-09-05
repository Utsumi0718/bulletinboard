package com.example.bulletinboard.dto.topic;

import java.time.LocalDateTime;

/*
 * 【クラスの役割】
 * Topicの情報をAPIからクライアントへ返すためのDTOです。
 *
 * Topic EntityやUser Entityを直接レスポンスとして返さず、
 * 画面表示に必要な情報だけを返します。
 *
 * ユーザー情報については、
 * emailなどの非公開情報は含めず、
 * 公開表示用のusernameのみを保持します。
 */
public class TopicResponse {

    private Long id;

    private String title;

    private String image;

    private String question;

    private String username;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}