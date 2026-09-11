package com.example.bulletinboard.controller.api;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
import com.example.bulletinboard.service.LikeService;
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
 *
 * - GET /api/topics/{topicId}/answers
 *   → 指定TopicのAnswer一覧取得
 *   → 現在ログインしているユーザーのliked状態を返す
 *   → AnswerごとのlikeCountを返す
 *   → Like情報は一括取得し、Answerごとの個別Queryを避ける
 *   → 正常時 200 OK
 *   → Answerが0件の場合は空配列
 *   → Topic不存在時 404 Not Found
 *
 * - POST /api/topics/{topicId}/answers
 *   → 指定TopicへのAnswer投稿
 *   → 正常時 201 Created
 *   → 作成直後のLike情報は
 *      liked = false
 *      likeCount = 0
 *   → Validationエラー時 400 Bad Request
 *   → Topic不存在時 404 Not Found
 *   → ログインユーザー取得失敗時 500 Internal Server Error
 *
 * - PUT /api/answers/{id}
 *   → Answerの編集
 *   → AuthenticationからloginEmailを取得
 *   → AnswerRequestのcontentをAnswerServiceへ渡す
 *   → 正常時 200 OKとAnswerResponse
 *   → 更新成功時はLikeが0件であるため
 *      liked = false
 *      likeCount = 0
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
 * - Likeに関する取得処理はLikeServiceへ委譲します。
 * - 認証PrincipalにはログインIDであるemailが設定されます。
 * - GET一覧ではAnswer ID一覧を作成し、
 *   Like件数とliked対象Answer IDを一括取得します。
 * - AnswerごとにLikeRepositoryへ問い合わせる方式を避け、
 *   Like関連のN+1問題を防ぎます。
 * - REST APIの例外レスポンスはGlobalExceptionHandlerへ委譲します。
 */
@RestController
@RequestMapping("/api")
public class AnswerApiController {

    private final AnswerService answerService;
    private final TopicService topicService;
    private final CustomUserDetailsService userDetailsService;
    private final LikeService likeService;

    public AnswerApiController(
            AnswerService answerService,
            TopicService topicService,
            CustomUserDetailsService userDetailsService,
            LikeService likeService) {

        this.answerService = answerService;
        this.topicService = topicService;
        this.userDetailsService = userDetailsService;
        this.likeService = likeService;
    }

    /**
     * 指定されたTopicに紐づくAnswer一覧を取得します。
     *
     * TopicServiceを使用して対象Topicが存在することを確認します。
     *
     * Authenticationからログインユーザーのemailを取得し、
     * CustomUserDetailsServiceを使用して現在のUserを取得します。
     *
     * Answer一覧取得後にAnswer ID一覧を作成し、
     * LikeServiceを使用して以下の情報を一括取得します。
     *
     * - AnswerごとのLike件数
     * - 現在ログインしているユーザーがLike済みのAnswer ID
     *
     * AnswerごとにLike件数確認・liked確認のQueryを実行せず、
     * 一括取得したMapとSetを使用してAnswerResponseを生成します。
     *
     * Answerが0件の場合は空Listを200 OKで返します。
     *
     * @param topicId        Answer一覧を取得する対象TopicのID
     * @param authentication ログインユーザーの認証情報
     * @return Like情報を含むAnswerResponse一覧と200 OK
     */
    @GetMapping("/topics/{topicId}/answers")
    public ResponseEntity<List<AnswerResponse>> getAnswersByTopicId(
            @PathVariable Long topicId,
            Authentication authentication) {

        /*
         * Topicが存在し、
         * 論理削除されていないことを確認します。
         */
        topicService.getById(topicId);

        /*
         * 認証情報からログインIDであるemailを取得します。
         */
        String loginEmail = authentication.getName();

        /*
         * loginEmailから現在ログインしているUserを取得します。
         *
         * liked状態を一括取得する際には、
         * User EntityではなくUser IDを使用します。
         */
        User currentUser = userDetailsService
                .findByEmail(loginEmail)
                .orElseThrow(() ->
                        new UserNotFoundException(
                                "ログインユーザー情報を取得できませんでした。"
                        )
                );

        /*
         * Topicに紐づく、
         * 論理削除されていないAnswer一覧を取得します。
         */
        List<Answer> answers =
                answerService.getAnswersByTopicId(topicId);

        /*
         * Like情報を一括取得するために、
         * Answer IDだけをListとして取り出します。
         */
        List<Long> answerIds =
                answers.stream()
                        .map(Answer::getId)
                        .toList();

        /*
         * AnswerごとのLike件数を一括取得します。
         *
         * key   : Answer ID
         * value : Like件数
         *
         * Likeが0件のAnswerについては、
         * LikeService側で0として補完されます。
         */
        Map<Long, Long> likeCounts =
                likeService.getLikeCountsByAnswerIds(
                        answerIds
                );

        /*
         * 現在ログインしているユーザーが
         * Like済みのAnswer IDを一括取得します。
         *
         * Setを使用することで、
         * 各Answerについてcontains()で
         * liked状態を判定できます。
         */
        Set<Long> likedAnswerIds =
                likeService.getLikedAnswerIdsByUserId(
                        currentUser.getId(),
                        answerIds
                );

        /*
         * Answer Entityと一括取得したLike情報を組み合わせ、
         * AnswerResponseへ変換します。
         *
         * ここではDBへの追加問い合わせは行わず、
         * MapとSetを使用してliked / likeCountを設定します。
         */
        List<AnswerResponse> responses =
                answers.stream()
                        .map(answer -> {

                            boolean liked =
                                    likedAnswerIds.contains(
                                            answer.getId()
                                    );

                            long likeCount =
                                    likeCounts.getOrDefault(
                                            answer.getId(),
                                            0L
                                    );

                            return AnswerResponse.from(
                                    answer,
                                    liked,
                                    likeCount
                            );
                        })
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
     * 作成直後はLikeが存在しないため、
     * AnswerResponseのLike情報は
     * liked = false、likeCount = 0となります。
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

        /*
         * 作成直後はLikeが存在しないため、
         * 1引数版from()の初期値
         *
         * liked = false
         * likeCount = 0
         *
         * を使用します。
         */
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
     * Likeが1件以上付いているAnswerは編集できないため、
     * 更新成功時はLike件数0であることが保証されます。
     *
     * そのため更新成功時のAnswerResponseでは、
     * liked = false、likeCount = 0となります。
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
     * 投稿者本人またはROLE_ADMINのみ削除可能です。
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