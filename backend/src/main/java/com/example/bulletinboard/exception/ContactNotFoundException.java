package com.example.bulletinboard.exception;

/**
 * 【クラスの役割】
 * 指定されたお問い合わせが存在しない場合に発生させる業務例外です。
 *
 * 管理Serviceの詳細取得・状態変更・削除処理で使用します。
 * 物理削除済みのお問い合わせも対象不存在として扱います。
 *
 * HTTPステータスへの変換はGlobalExceptionHandlerで行います。
 */
public class ContactNotFoundException extends RuntimeException {

    public ContactNotFoundException() {
        super("指定されたお問い合わせが見つかりません。");
    }
}