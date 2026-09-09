package com.example.bulletinboard.dto.answer;

import java.time.LocalDateTime;

import com.example.bulletinboard.model.Answer;

/*
 * 【クラスの役割】
 * Answerの情報をAPIからクライアントへ返すためのDTOです。
 *
 * Answer EntityやUser Entityを直接レスポンスとして返さず、
 * 画面表示に必要な情報だけを返します。
 *
 * ユーザー情報については、
 * emailなどの非公開情報は含めず、
 * 公開表示用のusernameのみを保持します。
 */
public class AnswerResponse {

    private Long id;

    private String content;

    private String username;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /*
     * Answer EntityをAPI返却用のAnswerResponseへ変換します。
     *
     * User Entity自体はレスポンスへ含めず、
     * 公開表示用のusernameのみを取り出します。
     */
    public static AnswerResponse from(Answer answer) {

        AnswerResponse response = new AnswerResponse();

        response.setId(answer.getId());
        response.setContent(answer.getContent());
        response.setUsername(answer.getUser().getUsername());
        response.setCreatedAt(answer.getCreatedAt());
        response.setUpdatedAt(answer.getUpdatedAt());

        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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