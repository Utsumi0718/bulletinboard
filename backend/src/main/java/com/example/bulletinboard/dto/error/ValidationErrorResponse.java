package com.example.bulletinboard.dto.error;

import java.util.Map;

/**
 * 【クラスの役割】
 * Validationエラーが発生した際に、
 * フィールドごとのエラーメッセージを
 * フロントエンドへ返すためのレスポンスDTOです。
 *
 * @param status      HTTPステータスコード
 * @param error       エラー種別
* @param message     Validation全体のメッセージ
 * @param path        エラーが発生したAPIパス
 * @param fieldErrors フィールド名とエラーメッセージ
 */
public record ValidationErrorResponse(
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors
) {
}