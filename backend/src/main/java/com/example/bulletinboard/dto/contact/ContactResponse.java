package com.example.bulletinboard.dto.contact;

/**
 * 【クラスの役割】
 * お問い合わせの受付成功時に、
 * フロントエンドへ返すレスポンスDTOです。
 *
 * お問い合わせ本文・メールアドレス・管理情報は含めず、
 * 受付完了のメッセージだけを返します。
 *
 * @param message 受付完了メッセージ
 */
public record ContactResponse(
        String message
) {
}