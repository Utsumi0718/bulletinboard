package com.example.bulletinboard.dto.contact;

import java.time.LocalDateTime;

import com.example.bulletinboard.model.Contact;
import com.example.bulletinboard.model.ContactCategory;
import com.example.bulletinboard.model.ContactStatus;

/**
 * 【クラスの役割】
 * 管理者向けのお問い合わせ詳細を返すレスポンスDTOです。
 *
 * メールアドレス・本文と種別を保持します。
 * nameとcategoryは旧データとの互換性のためnullを許容します。
 *
 * 【使用場面】
 * - お問い合わせの詳細取得
 * - ステータス変更後の詳細情報の返却
 *
 * 管理者専用APIで使用し、一般向けの受付APIでは使用しません。
 *
 * @param id        お問い合わせID
 * @param name      過去の受付の名前。新規受付ではnull
 * @param email     連絡先メールアドレス
 * @param subject   件名
 * @param message   お問い合わせ本文
 * @param status    対応状況
 * @param createdAt 受付日時
 * @param updatedAt 更新日時
 * @param category  種別。過去の受付で未設定の場合はnull
 * @param legalHoldUntil 法的保持の期限（JST）
 * @param legalHoldReason 法的保持の理由。管理者のみに返す
 */
public record AdminContactResponse(
        Long id,
        String name,
        String email,
        String subject,
        String message,
        ContactStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        ContactCategory category,
        LocalDateTime legalHoldUntil,
        String legalHoldReason
) {

    /**
     * Contactを管理者向けの詳細DTOへ変換します。
     *
     * @param contact 変換元のお問い合わせ
     * @return 詳細表示用のレスポンス
     */
    public static AdminContactResponse from(Contact contact) {
        return new AdminContactResponse(
                contact.getId(),
                contact.getName(),
                contact.getEmail(),
                contact.getSubject(),
                contact.getMessage(),
                contact.getStatus(),
                contact.getCreatedAt(),
                contact.getUpdatedAt(),
                contact.getCategory(),
                contact.getLegalHoldUntil(),
                contact.getLegalHoldReason()
        );
    }
}
