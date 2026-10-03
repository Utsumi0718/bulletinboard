package com.example.bulletinboard.controller.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.bulletinboard.dto.contact.ContactRequest;
import com.example.bulletinboard.dto.contact.ContactResponse;
import com.example.bulletinboard.service.ContactSubmissionService;

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

    private final ContactSubmissionService submissionService;

    public ContactApiController(
            ContactSubmissionService submissionService) {

        this.submissionService = submissionService;
    }

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