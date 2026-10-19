package com.example.bulletinboard.dto.contact;

import java.time.LocalDateTime;

import com.example.bulletinboard.model.Contact;
import com.example.bulletinboard.model.ContactCategory;
import com.example.bulletinboard.model.ContactStatus;

/**
 * 【クラスの役割】
 * 管理者向けのお問い合わせ一覧で使用するレスポンスDTOです。
 *
 * 過去のnameと新規のcategoryを返し、
 * メールアドレス・本文・更新日時は含めません。
 *
 * 【使用方法】
 * Contactからfrom()で変換し、
 * PageResponseのcontentに格納して返します。
 *
 * @param id        お問い合わせID
 * @param name      過去の受付の名前。新規受付ではnull
 * @param subject   件名
 * @param status    対応状況
 * @param createdAt 受付日時
 * @param category  種別。過去の受付で未設定の場合はnull
 */
public record AdminContactListResponse(
        Long id,
        String name,
        String subject,
        ContactStatus status,
        LocalDateTime createdAt,
        ContactCategory category
) {

    /**
     * Contactを管理者向けの一覧DTOへ変換します。
     *
     * @param contact 変換元のお問い合わせ
     * @return 一覧表示用のレスポンス
     */
    public static AdminContactListResponse from(Contact contact) {
        return new AdminContactListResponse(
                contact.getId(),
                contact.getName(),
                contact.getSubject(),
                contact.getStatus(),
                contact.getCreatedAt(),
                contact.getCategory()
        );
    }
}
