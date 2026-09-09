package com.example.bulletinboard.exception;

/**
 * 【クラスの役割】
 * ログイン中のユーザー情報を取得できなかった場合に
 * 使用する独自例外です。
 *
 * 認証情報として保持しているemailに対応するUserが
 * アプリケーション内に存在しない場合などに使用します。
 *
 * REST APIではGlobalExceptionHandlerで受け取り、
 * 500 Internal Server Errorへ変換する想定です。
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String message) {
        super(message);
    }
}