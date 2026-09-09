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
 * TopicServiceへ処理を委譲します。
 *
 * 取得したTopicはDTOへ変換し、
 * JSON形式のレスポンスとして返します。
 *
 * 旧Thymeleaf用のTopicControllerとは責務を分離しており、
 * 本クラスではREST APIのみを担当します。
 *
 * 【現在の対応API】
 * - GET /api/topics
 *   → Topic一覧取得
 *   → Topic.titleの部分一致検索
 *   → ページング
 *   → 正常時 200 OK
 *
 * - GET /api/topics/{id}
 *   → Topic詳細取得
 *   → 正常時 200 OK
 *   → Topic不存在・論理削除済みの場合は404 Not Found
 *
 * - POST /api/topics
 *   → Topic新規投稿
 *   → 正常時 201 Created
 *   → Locationヘッダーに作成済みTopicのURLを設定
 *   → Validationエラー時 400 Bad Request
 *   → ログインユーザー取得失敗時 500 Internal Server Error
 *
 * - PUT /api/topics/{id}
 *   → Topic編集
 *   → 投稿者本人のみ編集可能
 *   → Answerが一度でも投稿されたTopicは編集不可
 *   → 正常時 200 OK
 *   → Validationエラー時 400 Bad Request
 *   → 権限がない場合は403 Forbidden
 *   → Topic不存在・論理削除済みの場合は404 Not Found
 *   → Answer投稿履歴がある場合は409 Conflict
 *
 * - DELETE /api/topics/{id}
 *   → Topicの論理削除
 *   → 投稿者本人またはROLE_ADMINのみ削除可能
 *   → 正常時 204 No Content
 *   → 権限がない場合は403 Forbidden
 *   → Topic不存在・論理削除済みの場合は404 Not Found
 *
 * 【設計上のポイント】
 * - 認証PrincipalにはログインIDであるemailが設定されます。
 * - Topicに関する業務ルールはTopicServiceへ委譲します。
 * - ControllerではHTTPリクエストの受付、
 *   認証情報の取得、DTO変換、HTTPレスポンス生成を担当します。
 * - REST APIの例外レスポンスはGlobalExceptionHandlerへ委譲します。
 * - Thymeleaf用のView、redirect、FlashMessageは扱いません。
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
     * Topic一覧を取得します。
     *
     * keywordが未指定の場合は、
     * 作成日時の降順でTopic一覧を取得します。
     *
     * keywordが指定された場合は、
     * Topic.titleのみを対象として部分一致検索を行います。
     *
     * keywordが空文字または空白の場合は、
     * IllegalArgumentExceptionを発生させます。
     *
     * 取得結果はTopicListResponseへ変換し、
     * PageResponseとして200 OKで返します。
     *
     * @param page    ページ番号（0始まり）
     * @param keyword Topic.titleを部分一致検索するキーワード
     * @return Topic一覧のページ情報と200 OK
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
      * 指定されたTopicの詳細を取得します。
      *
      * TopicServiceから削除されていないTopicを取得し、
      * TopicResponseへ変換して200 OKで返します。
      *
      * Topicが存在しない、または論理削除済みの場合は
      * TopicNotFoundExceptionが発生し、
      * GlobalExceptionHandlerによって404 Not Foundになります。
      *
      * @param id 取得対象TopicのID
      * @return Topic詳細情報と200 OK
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
 * 新しいTopicを投稿します。
 *
 * Authenticationからログインユーザーのemailを取得し、
 * CustomUserDetailsServiceを使用してUserを取得します。
 *
 * TopicRequestのtitle・image・questionと
 * ログインユーザーをTopicへ設定し、
 * TopicServiceへ保存処理を委譲します。
 *
 * ログインユーザー情報を取得できない場合は
 * UserNotFoundExceptionが発生します。
 *
 * リクエスト内容がValidationに違反した場合は
 * 400 Bad Requestになります。
 *
 * 正常時は201 Createdを返し、
 * Locationヘッダーに作成されたTopicのURLを設定します。
 *
 * @param request        Topic投稿内容
 * @param authentication ログインユーザーの認証情報
 * @return 作成されたTopicResponseと201 Created
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
 * Authenticationからログインユーザーのemailを取得し、
 * TopicRequestのtitle・image・questionとともに
 * TopicServiceへ編集処理を委譲します。
 *
 * 投稿者本人かどうかの判定や、
 * Answerが一度でも投稿されたTopicを編集できないという
 * 業務ルールはTopicServiceで判定します。
 *
 * Topicが存在しない、または論理削除済みの場合は
 * 404 Not Foundになります。
 *
 * 編集権限がない場合は
 * 403 Forbiddenになります。
 *
 * Answer投稿履歴があり編集できない場合は
 * 409 Conflictになります。
 *
 * リクエスト内容がValidationに違反した場合は
 * 400 Bad Requestになります。
 *
 * 正常時は更新後のTopicをTopicResponseへ変換し、
 * 200 OKで返します。
 *
 * @param id             編集対象TopicのID
 * @param request        Topic編集内容
 * @param authentication ログインユーザーの認証情報
 * @return 更新後のTopicResponseと200 OK
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
 * Authenticationからログインユーザーのemailを取得し、
 * ROLE_ADMIN権限を持っているかを判定します。
 *
 * TopicServiceへTopic ID・loginEmail・管理者判定結果を渡し、
 * 削除可否の判定と論理削除処理を委譲します。
 *
 * 削除できるのは投稿者本人またはROLE_ADMINです。
 *
 * Topicが存在しない、または論理削除済みの場合は
 * 404 Not Foundになります。
 *
 * 削除権限がない場合は
 * 403 Forbiddenになります。
 *
 * 正常時は204 No Contentを返し、
 * Response Bodyは返しません。
 *
 * @param id             削除対象TopicのID
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