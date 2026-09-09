package com.example.bulletinboard.controller.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.bulletinboard.dto.answer.AnswerRequest;
import com.example.bulletinboard.dto.answer.AnswerResponse;
import com.example.bulletinboard.exception.UserNotFoundException;
import com.example.bulletinboard.model.Answer;
import com.example.bulletinboard.model.Topic;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.service.AnswerService;
import com.example.bulletinboard.service.CustomUserDetailsService;
import com.example.bulletinboard.service.TopicService;

import jakarta.validation.Valid;


 /*
 * 【クラスの役割】
 * Answerに関するREST APIを提供するControllerです。
 *
 * Answer一覧取得、投稿、編集、削除を担当します。
 *
 * 業務ルールやデータ取得・保存処理はService層へ委譲し、
 * ControllerではHTTPリクエストの受け取り、
 * 認証情報の取得、Response DTOへの変換を行います。
 *
 * 【現在の対応API】
 * - GET /api/topics/{topicId}/answers
 *   → 指定TopicのAnswer一覧取得
 *   → 正常時 200 OK
 *   → Answerが0件の場合は空配列
 *   → Topic不存在時 404 Not Found
 *
 * - POST /api/topics/{topicId}/answers
 *   → 指定TopicへのAnswer投稿
 *   → 正常時 201 Created
 *   → Validationエラー時 400 Bad Request
 *   → Topic不存在時 404 Not Found
 *   → ログインユーザー取得失敗時 500 Internal Server Error
 *
 * - PUT /api/answers/{id}
 *   → Answerの編集
 *   → AuthenticationからloginEmailを取得
 *   → AnswerRequestのcontentをAnswerServiceへ渡す
 *   → 正常時 200 OKとAnswerResponse
 *   → Answer不存在時 404 Not Found
 *   → 編集権限がない場合は403 Forbidden
 *   → Likeが付いている場合は409 Conflict
 *   → Validationエラー時は400 Bad Request
 *
 * - DELETE /api/answers/{id}
 *   → Answerの論理削除
 *   → AuthenticationからloginEmailを取得
 *   → ROLE_ADMIN判定
 *   → 投稿者本人またはROLE_ADMINのみ削除可能
 *   → 正常時 204 No Content
 *   → Answer不存在時 404 Not Found
 *   → 削除権限がない場合は403 Forbidden
 *
 * 【設計上のポイント】
 * - ControllerではHTTP層の責務に集中します。
 * - Answerに関する業務ルールはAnswerServiceへ委譲します。
 * - 認証PrincipalにはログインIDであるemailが設定されます。
 * - REST APIの例外レスポンスはGlobalExceptionHandlerへ委譲します。
 */


@RestController
@RequestMapping("/api")
public class AnswerApiController {

  private final AnswerService answerService;
  private final TopicService topicService;
  private final CustomUserDetailsService userDetailsService;


  public AnswerApiController(
              AnswerService answerService,
              TopicService topicService,
             CustomUserDetailsService userDetailsService){
      this.answerService = answerService;
      this.topicService = topicService;
      this.userDetailsService = userDetailsService;
  }

  /**
 * 指定されたTopicに紐づくAnswer一覧を取得します。
 *
 * まずTopicServiceを使用して対象Topicが存在することを確認します。
 * Topicが存在しない、または論理削除済みの場合は、
 * TopicNotFoundExceptionが発生し、
 * GlobalExceptionHandlerによって404 Not Foundとして返されます。
 *
 * Topicが存在する場合は、
 * AnswerServiceから削除されていないAnswer一覧を取得し、
 * AnswerResponseへ変換して返します。
 *
 * Answerが0件の場合でもエラーにはせず、
 * 空のListを200 OKで返します。
 *
 * @param topicId Answer一覧を取得する対象TopicのID
 * @return AnswerResponseの一覧と200 OK
 */
@GetMapping("/topics/{topicId}/answers")
public ResponseEntity<List<AnswerResponse>> getAnswersByTopicId(
        @PathVariable Long topicId) {

    topicService.getById(topicId);

    List<AnswerResponse> responses =
            answerService.getAnswersByTopicId(topicId)
                    .stream()
                    .map(AnswerResponse::from)
                    .toList();

    return ResponseEntity.ok(responses);
}

/**
 * 指定されたTopicへ新しいAnswerを投稿します。
 *
 * Authenticationからログインユーザーのemailを取得し、
 * CustomUserDetailsServiceを使ってUserを取得します。
 *
 * topicIdから投稿対象のTopicを取得し、
 * User・Topic・contentをAnswerへ設定して保存します。
 *
 * Topicが存在しない、または論理削除済みの場合は
 * TopicNotFoundExceptionが発生します。
 *
 * ログインユーザー情報を取得できない場合は
 * UserNotFoundExceptionが発生します。
 *
 * リクエスト内容がValidationに違反した場合は
 * 400 Bad Requestになります。
 *
 * 正常時は201 CreatedとAnswerResponseを返します。
 *
 * @param topicId        回答を投稿するTopicのID
 * @param request        回答内容
 * @param authentication ログインユーザーの認証情報
 * @return 作成されたAnswerResponseと201 Created
 */
@PostMapping("/topics/{topicId}/answers")
public ResponseEntity<AnswerResponse> createAnswer(
        @PathVariable Long topicId,
        @Valid @RequestBody AnswerRequest request,
        Authentication authentication) {

    String loginEmail = authentication.getName();

    User currentUser = userDetailsService
            .findByEmail(loginEmail)
            .orElseThrow(() ->
                    new UserNotFoundException(
                            "ログインユーザー情報を取得できませんでした。"
                    )
            );

    Topic topic = topicService.getById(topicId);

    Answer answer = new Answer();
    answer.setTopic(topic);
    answer.setUser(currentUser);
    answer.setContent(request.getContent());

    Answer savedAnswer =
            answerService.saveAnswer(answer);

    AnswerResponse response =
            AnswerResponse.from(savedAnswer);

    return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(response);
}

/**
 * 指定されたAnswerを編集します。
 *
 * Authenticationからログインユーザーのemailを取得し、
 * AnswerRequestのcontentとともに
 * AnswerServiceへ編集処理を委譲します。
 *
 * Answerが存在しない、または論理削除済みの場合は
 * AnswerNotFoundExceptionが発生し、
 * 404 Not Foundになります。
 *
 * 投稿者本人以外が編集しようとした場合は
 * ForbiddenOperationExceptionが発生し、
 * 403 Forbiddenになります。
 *
 * Likeが1件以上付いているAnswerを編集しようとした場合は
 * AnswerEditConflictExceptionが発生し、
 * 409 Conflictになります。
 *
 * リクエスト内容がValidationに違反した場合は
 * 400 Bad Requestになります。
 *
 * 正常時は更新後のAnswerをAnswerResponseへ変換し、
 * 200 OKで返します。
 *
 * @param id             編集対象AnswerのID
 * @param request        更新する回答内容
 * @param authentication ログインユーザーの認証情報
 * @return 更新されたAnswerResponseと200 OK
 */
@PutMapping("/answers/{id}")
public ResponseEntity<AnswerResponse> updateAnswer(
        @PathVariable Long id,
        @Valid @RequestBody AnswerRequest request,
        Authentication authentication) {

    String loginEmail = authentication.getName();

    Answer updatedAnswer = answerService.updateAnswer(
            id,
            loginEmail,
            request.getContent()
    );

    AnswerResponse response =
            AnswerResponse.from(updatedAnswer);

    return ResponseEntity.ok(response);
}

/**
 * 指定されたAnswerを論理削除します。
 *
 * Authenticationからログインユーザーのemailを取得し、
 * ROLE_ADMIN権限を持っているかを判定します。
 *
 * AnswerServiceへAnswer ID・loginEmail・管理者判定結果を渡し、
 * 削除処理を委譲します。
 *
 * 削除できるのは投稿者本人またはROLE_ADMINです。
 *
 * Answerが存在しない、または論理削除済みの場合は
 * AnswerNotFoundExceptionが発生し、
 * 404 Not Foundになります。
 *
 * 削除権限がない場合は
 * ForbiddenOperationExceptionが発生し、
 * 403 Forbiddenになります。
 *
 * 正常時は204 No Contentを返し、
 * Response Bodyは返しません。
 *
 * @param id             削除対象AnswerのID
 * @param authentication ログインユーザーの認証情報
 * @return 204 No Content
 */

@DeleteMapping("/answers/{id}")
public ResponseEntity<Void> deleteAnswer(
        @PathVariable Long id,
        Authentication authentication) {

    String loginEmail = authentication.getName();

    boolean isAdmin = authentication.getAuthorities()
            .stream()
            .anyMatch(
                    authority ->
                            authority.getAuthority()
                                    .equals("ROLE_ADMIN")
            );

    answerService.deleteAnswer(
            id,
            loginEmail,
            isAdmin
    );

    return ResponseEntity.noContent().build();
}
}
