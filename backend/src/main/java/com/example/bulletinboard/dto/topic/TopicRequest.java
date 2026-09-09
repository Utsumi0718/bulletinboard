package com.example.bulletinboard.dto.topic;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/*
 * 【クラスの役割】
 * Topicの新規投稿・編集時に、
 * クライアントから受け取る入力データを表すDTOです。
 *
 * APIではTopic Entityを直接リクエストとして受け取らず、
 * 必要な入力項目だけをTopicRequestとして受け取ります。
 */
public class TopicRequest {

    @NotBlank
    @Size(max = 50)
    private String title;

    @NotBlank
    @Size(max = 255)
    private String image;

    @NotBlank
    @Size(max = 100)
    private String question;

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
}