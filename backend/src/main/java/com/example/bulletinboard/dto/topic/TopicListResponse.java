package com.example.bulletinboard.dto.topic;

import java.time.LocalDateTime;

import com.example.bulletinboard.model.Topic;

/**
 * 【クラスの役割】
 * Topic一覧画面へ返すためのレスポンスDTOです。
 *
 * 一覧表示に必要な情報だけを保持し、
 * Topic Entityを直接APIレスポンスとして返さないようにします。
 *
 * @param id        Topic ID
 * @param title     お題タイトル
 * @param image     お題画像
 * @param createdAt 作成日時
 */
public record TopicListResponse(
        Long id,
        String title,
        String image,
        LocalDateTime createdAt
) {

    /**
     * Topic EntityをTopicListResponseへ変換します。
     *
     * @param topic 変換元Topic
     * @return 一覧表示用TopicListResponse
     */
    public static TopicListResponse from(Topic topic) {
        return new TopicListResponse(
                topic.getId(),
                topic.getTitle(),
                topic.getImage(),
                topic.getCreatedAt()
        );
    }
}