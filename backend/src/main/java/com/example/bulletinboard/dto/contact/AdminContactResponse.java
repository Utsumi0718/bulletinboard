package com.example.bulletinboard.dto.contact;

import java.time.LocalDateTime;

import com.example.bulletinboard.model.Contact;
import com.example.bulletinboard.model.ContactStatus;

/**
 * 【クラスの役割】
 * 管理者向けのお問い合わせ詳細を返すレスポンスDTOです。
 *
 * 一覧の5項目にメールアドレス・本文・更新日時を加えた、
 * 合計8項目を保持します。
 *
 * 【使用場面】
 * - お問い合わせの詳細取得
 * - ステータス変更後の詳細情報の返却
 *
 * 管理者専用APIで使用し、一般向けの受付APIでは使用しません。
 *
 * @param id        お問い合わせID
 * @param name      送信者の名前
 * @param email     連絡先メールアドレス
 * @param subject   件名
 * @param message   お問い合わせ本文
 * @param status    対応状況
 * @param createdAt 受付日時
 * @param updatedAt 更新日時
 */
public record AdminContactResponse(
        Long id,
        String name,
        String email,
        String subject,
        String message,
        ContactStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
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
                contact.getUpdatedAt()
        );
    }
}