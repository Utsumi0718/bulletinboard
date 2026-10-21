package com.example.bulletinboard.controller.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.core.Authentication;
import org.springframework.http.CacheControl;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.bulletinboard.dto.contact.ContactRequest;
import com.example.bulletinboard.dto.contact.ContactResponse;
import com.example.bulletinboard.service.ContactSubmissionService;
import com.example.bulletinboard.repository.UserRepository;
import com.example.bulletinboard.model.AccountStatus;
import com.example.bulletinboard.exception.UserNotFoundException;
import com.example.bulletinboard.exception.ForbiddenOperationException;

import jakarta.validation.Valid;

/**
 * 【クラスの役割】
 * お問い合わせ受付のREST APIを提供するControllerです。
 *
 * 【主な役割】
 * - JSON形式の入力をContactRequestとして受け取る
 * - 入力条件を検証する
 * - ContactSubmissionServiceへ受付処理を委譲する
 * - 受付成功時に201 Createdと受付完了メッセージを返す
 *
 * DB保存やメール通知の処理はService側で行います。
 * 未ログイン利用の許可とCSRF保護はSecurityConfig側で設定します。
 */
@RestController
@RequestMapping("/api/contacts")
public class ContactApiController {

    private static final Logger log = LoggerFactory.getLogger(ContactApiController.class);

    private final ContactSubmissionService submissionService;
    private final UserRepository users;

    public ContactApiController(
            ContactSubmissionService submissionService, UserRepository users) {

        this.submissionService = submissionService;
        this.users = users;
    }

    /** 認証済み本人の入力補助。対象User IDやメールをクライアントから受け取らない。 */
    @GetMapping("/prefill")
    public ResponseEntity<ContactPrefillResponse> prefill(Authentication authentication) {
        try {
            var user = users.findById(com.example.bulletinboard.security.ActorIds.parse(authentication.getName()))
                    .orElseThrow(() -> new UserNotFoundException("ログインユーザー情報を取得できませんでした。"));
            if (user.getAccountStatus() != AccountStatus.ACTIVE) {
                throw new ForbiddenOperationException("このリクエストは許可されていません。");
            }
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ContactPrefillResponse(user.getEmail()));
        } catch (DataAccessException | TransactionException ex) {
            log.error("contact_prefill_error errorType={}", ex.getClass().getSimpleName());
            throw new UserNotFoundException("ログインユーザー情報を取得できませんでした。");
        }
    }

    public record ContactPrefillResponse(String email) { }

    /**
     * お問い合わせを受け付けます。
     *
     * 入力検証に失敗した場合はServiceを呼び出さず、
     * 共通例外処理で入力エラーレスポンスを返します。
     */
    @PostMapping
    public ResponseEntity<ContactResponse> submitContact(
            @Valid @RequestBody ContactRequest request) {

        submissionService.submitContact(request);

        ContactResponse response = new ContactResponse(
                "お問い合わせを受け付けました。"
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
