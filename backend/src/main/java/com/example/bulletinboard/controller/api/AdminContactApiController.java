package com.example.bulletinboard.controller.api;

import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.bulletinboard.dto.common.PageResponse;
import com.example.bulletinboard.dto.contact.AdminContactListResponse;
import com.example.bulletinboard.dto.contact.AdminContactResponse;
import com.example.bulletinboard.dto.contact.AdminContactStatusRequest;
import com.example.bulletinboard.exception.AdminContactOperationException;
import com.example.bulletinboard.exception.AdminContactOperationException.Operation;
import com.example.bulletinboard.model.Contact;
import com.example.bulletinboard.model.ContactStatus;
import com.example.bulletinboard.service.AdminContactService;

import jakarta.validation.Valid;

/**
 * 【クラスの役割】
 * 管理者向けのお問い合わせ管理APIを提供するControllerです。
 *
 * 【現在の対応API】
 * GET /api/admin/contacts
 * - ページングとステータスによる絞り込みに対応します。
 * - 作成日時の降順、同じ日時の場合はIDの降順で取得します。
 * - 一覧用DTOとページ情報を200 OKで返します。
 * - ページ番号・件数の範囲外、ステータス不正は400になります。
 *
 * GET /api/admin/contacts/{id}
 * - 正常時は詳細情報を200 OKで返します。
 * - 対象不存在時はGlobalExceptionHandlerを通して
 *   404 Not FoundとErrorResponseを返します。
 *
 * - PATCH /api/admin/contacts/{id}/status
 *   → お問い合わせの状態変更
 *   → 認証情報から操作する管理者を特定
 *   → 状態変更と操作履歴保存をServiceへ委譲
 *   → 正常時は200と詳細情報を返却
 *   → 同じ状態の場合は更新せず、現在の詳細情報を返却
 *
 * - DELETE /api/admin/contacts/{id}
 *   → お問い合わせの物理削除と操作履歴保存
 *   → 正常時は204 No Content
 *   → 対象不存在・削除済みIDへの再実行は404 Not Found
 *
 * 【役割分担】
 * - お問い合わせの取得・状態変更・削除と操作履歴保存は
 *   AdminContactServiceへ委譲します。
 * - このクラスではDTOへの変換とHTTP応答を担当します。
 * - /api/admin/**の管理者認可はSecurityConfigで行います。
 *
 * - 状態変更・削除のService呼び出しから伝わるDataAccessException・
 *   TransactionExceptionをAdminContactOperationExceptionへ変換します。
 * - GlobalExceptionHandlerが運営用の失敗ログと共通500応答を生成します。
 * - 対象不存在や入力不正の例外は管理操作専用例外へ変換せず、
 *   それぞれの共通エラー処理へ渡します。
 *
 */
@RestController
@RequestMapping("/api/admin/contacts")
public class AdminContactApiController {

    private final AdminContactService adminContactService;

    public AdminContactApiController(
            AdminContactService adminContactService) {
        this.adminContactService = adminContactService;
    }

    /**
     * 指定されたお問い合わせの詳細を取得します。
     *
     * @param id お問い合わせID
     * @return 管理者向けの詳細情報と200 OK
     */
    @GetMapping("/{id}")
    public ResponseEntity<AdminContactResponse> getContact(
            @PathVariable Long id) {

        Contact contact = adminContactService.getById(id);

        AdminContactResponse response =
                AdminContactResponse.from(contact);

        return ResponseEntity.ok(response);

	    }
    /**
     * 管理者向けのお問い合わせ一覧を取得します。
     * pageは省略時0、sizeは省略時20です。
     * statusが未指定の場合は全状態を取得します。
     *
     * @param page   ページ番号（0始まり）
     * @param size   1ページあたりの件数（1～100）
     * @param status 絞り込むステータス
     * @return 一覧とページ情報、200 OK
    */
@GetMapping
public ResponseEntity<PageResponse<AdminContactListResponse>> getContacts(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String status) {

    ContactStatus contactStatus = null;

    // 未指定と、不正な値の指定を区別する
    if (status != null) {
        try {
            contactStatus = ContactStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "指定されたステータスは無効です。"
            );
        }
    }

    Page<Contact> contacts = adminContactService.findContacts(
            page,
            size,
          contactStatus
    );

    Page<AdminContactListResponse> responsePage =
            contacts.map(AdminContactListResponse::from);

return ResponseEntity.ok(PageResponse.from(responsePage));
 }

/**
 * お問い合わせの対応状態を変更します。
 *
 * Service呼び出しを囲むことで、
 * 処理中・トランザクション終了時に伝わるDataAccessException・
 * TransactionExceptionを管理操作専用例外へ変換します。
 * 例外の発生だけでは、DBの最終状態やロールバック完了を断定しません。
 */
@PatchMapping("/{id}/status")
public ResponseEntity<AdminContactResponse> updateContactStatus(
        @PathVariable Long id,
        @Valid @RequestBody AdminContactStatusRequest request,
        Authentication authentication) {

    Contact contact;

    try {
        contact = adminContactService.updateStatus(
                id,
                request.status(),
                authentication.getName()
        );
    } catch (DataAccessException | TransactionException ex) {
        throw new AdminContactOperationException(
                Operation.STATUS_CHANGE,
                id,
                ex
        );
    }

    return ResponseEntity.ok(AdminContactResponse.from(contact));
}

/**
 * お問い合わせを物理削除します。
 *
 * Serviceのトランザクションが正常終了した場合に204を返します。
 * 状態変更と同じくDataAccessException・TransactionExceptionを
 * 管理操作専用例外へ変換し、失敗ログと共通500応答の処理へ渡します。
 */
@DeleteMapping("/{id}")
public ResponseEntity<Void> deleteContact(
        @PathVariable Long id,
        Authentication authentication) {

    try {
        adminContactService.deleteContact(
                id,
                authentication.getName()
        );
    } catch (DataAccessException | TransactionException ex) {
        throw new AdminContactOperationException(
                Operation.DELETE,
                id,
                ex
        );
    }

    return ResponseEntity.noContent().build();
}

}
