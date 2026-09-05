package com.example.bulletinboard.dto.topic;

import java.time.LocalDateTime;

import com.example.bulletinboard.model.Topic;

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

    /*
     * Topic EntityをAPI返却用のTopicResponseへ変換します。
     *
     * User Entity自体はレスポンスへ含めず、
     * 公開表示用のusernameのみを取り出します。
     */
    public static TopicResponse from(Topic topic) {

        TopicResponse response = new TopicResponse();

        response.setId(topic.getId());
        response.setTitle(topic.getTitle());
        response.setImage(topic.getImage());
        response.setQuestion(topic.getQuestion());
        response.setUsername(topic.getUser().getUsername());
        response.setCreatedAt(topic.getCreatedAt());
        response.setUpdatedAt(topic.getUpdatedAt());

        return response;
    }

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