package com.example.bulletinboard.controller.api;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.bulletinboard.dto.common.PageResponse;
import com.example.bulletinboard.dto.topic.TopicListResponse;
import com.example.bulletinboard.dto.topic.TopicResponse;
import com.example.bulletinboard.model.Topic;
import com.example.bulletinboard.service.TopicService;

/**
 * 【クラスの役割】
 * Topicに関するREST APIを提供するControllerです。
 *
 * Reactなどのフロントエンドから送信される
 * /api/topics 配下のHTTPリクエストを受け取り、
 * TopicServiceを呼び出して処理結果をDTOへ変換し、
 * JSON形式でレスポンスを返します。
 *
 * 旧Thymeleaf用のTopicControllerとは責務を分離し、
 * このクラスではREST APIのみを担当します。
 *
  * 【現在の対応内容】
 * - Topic一覧取得
 * - Topicタイトルの部分一致検索
 * - Topic詳細取得
 *
 * 【今後追加する内容】
 * - Topic新規投稿
 * - Topic編集
 * - Topic削除
 */
@RestController
@RequestMapping("/api/topics")
public class TopicApiController {

    private final TopicService topicService;

    public TopicApiController(TopicService topicService) {
        this.topicService = topicService;
    }

    /**
     * Topic一覧を取得します。
     *
     * keywordが未指定の場合は通常一覧を取得し、
     * keywordが指定された場合はTopic.titleを部分一致検索します。
     *
     * @param page    ページ番号（0始まり）
     * @param keyword 検索ワード
     * @return Topic一覧とページング情報
     */
    @GetMapping
    public ResponseEntity<PageResponse<TopicListResponse>> getTopics(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String keyword) {

        Page<Topic> topics;

        if (keyword == null) {
            topics = topicService.findAll(
                    page,
                    "createdAt",
                    "desc"
            );
        } else {

            if (keyword.isBlank()) {
                throw new IllegalArgumentException(
                        "検索ワードを入力してください"
                );
            }

            topics = topicService.searchTopics(
                    page,
                    keyword,
                    "createdAt",
                    "desc"
            );
        }

        Page<TopicListResponse> responsePage =
                topics.map(TopicListResponse::from);

        PageResponse<TopicListResponse> response =
                PageResponse.from(responsePage);

        return ResponseEntity.ok(response);
    }

    /**
      * Topic詳細を取得します。
      *
      * @param id Topic ID
      * @return Topic詳細情報
    */

    @GetMapping("/{id}")
    public ResponseEntity<TopicResponse> getTopic(
        @PathVariable Long id) {

    Topic topic = topicService.getById(id);

    TopicResponse response =
            TopicResponse.from(topic);

    return ResponseEntity.ok(response);
}
}