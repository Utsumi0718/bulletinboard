package com.example.bulletinboard.dto.answer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/*
 * 【クラスの役割】
 * Answerの新規投稿・編集時に、
 * クライアントから受け取る入力データを表すDTOです。
 *
 * APIではAnswer Entityを直接リクエストとして受け取らず、
 * 必要な入力項目だけをAnswerRequestとして受け取ります。
 */
public class AnswerRequest {

    @NotBlank
    @Size(max = 100)
    private String content;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}