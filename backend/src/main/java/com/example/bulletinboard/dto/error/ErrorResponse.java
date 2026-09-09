package com.example.bulletinboard.dto.error;

/**
 * 【クラスの役割】
 * APIでエラーが発生した際に、
 * フロントエンドへ返す共通エラーレスポンスDTOです。
 *
 * @param status  HTTPステータスコード
 * @param error   エラー種別
 * @param message エラーメッセージ
 * @param path    エラーが発生したAPIパス
 */
public record ErrorResponse(
        int status,
        String error,
        String message,
        String path
) {
}