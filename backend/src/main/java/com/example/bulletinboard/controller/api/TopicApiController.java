package com.example.bulletinboard.controller.api;

import java.net.URI;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.bulletinboard.dto.common.PageResponse;
import com.example.bulletinboard.dto.topic.TopicListResponse;
import com.example.bulletinboard.dto.topic.TopicRequest;
import com.example.bulletinboard.dto.topic.TopicResponse;
import com.example.bulletinboard.exception.UserNotFoundException;
import com.example.bulletinboard.model.Topic;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.service.CustomUserDetailsService;
import com.example.bulletinboard.service.TopicService;

import jakarta.validation.Valid;

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
 * - Topic新規投稿
 * - Topic編集
 * - Topic削除
 *
 *
 */

@RestController
@RequestMapping("/api/topics")
public class TopicApiController {

    private final TopicService topicService;
    private final CustomUserDetailsService userDetailsService;

     public TopicApiController(
        TopicService topicService,
        CustomUserDetailsService userDetailsService) {

    this.topicService = topicService;
    this.userDetailsService = userDetailsService;
   }

    /**
      * 新しいTopicを投稿します。
      *
      * ログイン中のユーザーをemailから取得し、
      * TopicRequestの入力内容と紐付けてTopicを保存します。
      *
      * 認証情報のemailに対応するUserが取得できない場合は、
      * UserNotFoundExceptionを発生させます。
      *
      * 投稿成功時は201 Createdを返し、
      * Locationヘッダーに作成されたTopicのURLを設定します。
      *
      * @param request        Topic投稿内容
      * @param authentication ログインユーザーの認証情報
      * @return 作成されたTopicの詳細情報
      * @throws UserNotFoundException ログインユーザー情報を取得できない場合
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



/**
 * Topic一覧を取得します。
 *
 * keywordが未指定の場合は通常一覧を取得し、
 * keywordが指定された場合はTopic.titleを部分一致検索します。
 *
 * keywordが空文字または空白の場合は
 * IllegalArgumentExceptionを発生させます。
 *
 * @param page    ページ番号（0始まり）
 * @param keyword 検索キーワード
 * @return Topic一覧のページ情報
 */
@PostMapping
public ResponseEntity<TopicResponse> createTopic(
        @Valid @RequestBody TopicRequest request,
        Authentication authentication) {

    String loginEmail = authentication.getName();

    User user = userDetailsService
            .findByEmail(loginEmail)
            .orElseThrow(() -> new UserNotFoundException(
        "ログインユーザー情報を取得できませんでした。"
          ));

    Topic topic = new Topic();
    topic.setTitle(request.getTitle());
    topic.setImage(request.getImage());
    topic.setQuestion(request.getQuestion());
    topic.setUser(user);
    Topic savedTopic = topicService.save(topic);

    TopicResponse response =
            TopicResponse.from(savedTopic);

    URI location =
            URI.create("/api/topics/" + savedTopic.getId());

    return ResponseEntity
            .created(location)
            .body(response);
}

/**
 * 指定されたTopicを編集します。
 *
 * ログインユーザーのemailをAuthenticationから取得し、
 * TopicRequestの内容を使ってTopicを更新します。
 *
 * 編集可能かどうかの本人確認や、
 * Answerが存在するTopicの編集可否は
 * TopicServiceで判定します。
 *
 * 更新成功時は200 OKと
 * 更新後のTopicResponseを返します。
 *
 * @param id             編集対象のTopic ID
 * @param request        Topic編集内容
 * @param authentication ログインユーザーの認証情報
 * @return 更新後のTopic詳細情報
 */
@PutMapping("/{id}")
public ResponseEntity<TopicResponse> updateTopic(
        @PathVariable Long id,
        @Valid @RequestBody TopicRequest request,
        Authentication authentication) {

    String loginEmail = authentication.getName();

    Topic updatedTopic = topicService.updateTopic(
            id,
            loginEmail,
            request.getTitle(),
            request.getImage(),
            request.getQuestion()
    );

    TopicResponse response =
            TopicResponse.from(updatedTopic);

    return ResponseEntity.ok(response);
}

/**
 * 指定されたTopicを論理削除します。
 *
 * ログインユーザーのemailをAuthenticationから取得し、
 * ROLE_ADMINを持っているか判定します。
 *
 * Topicの投稿者本人またはROLE_ADMINの場合のみ削除可能です。
 * 削除可否の最終判定と論理削除処理はTopicServiceで行います。
 *
 * 削除成功時は204 No Contentを返します。
 *
 * @param id             削除対象のTopic ID
 * @param authentication ログインユーザーの認証情報
 * @return 204 No Content
 */


@DeleteMapping("/{id}")
public ResponseEntity<Void> deleteTopic(
        @PathVariable Long id,
        Authentication authentication) {

    String loginEmail = authentication.getName();

    boolean isAdmin = authentication.getAuthorities()
            .stream()
            .anyMatch(authority ->
                    authority.getAuthority().equals("ROLE_ADMIN")
            );

    topicService.deleteById(
            id,
            loginEmail,
            isAdmin
    );

    return ResponseEntity.noContent().build();
}
}