package com.example.bulletinboard.exception;

/**
 * 【クラスの役割】
 * お問い合わせのDB保存に失敗したことを表す例外です。
 *
 * 【設計上のポイント】
 * - お問い合わせの保存失敗を、他のAPIのDBエラーと区別します。
 * - 元の例外を原因として保持します。
 * - メッセージにはSQLや接続情報などの内部情報を含めません。
 *
 * HTTPステータスとレスポンスへの変換は、
 * GlobalExceptionHandlerで行います。
 */
public class ContactSaveException extends RuntimeException {

    public ContactSaveException(Throwable cause) {
        super(
                "お問い合わせを受け付けられませんでした。"
                + "時間をおいて再度お試しください。",
                cause
        );
    }
}