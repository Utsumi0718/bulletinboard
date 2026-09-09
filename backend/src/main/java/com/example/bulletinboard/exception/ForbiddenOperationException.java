package com.example.bulletinboard.exception;

/**
 * 【クラスの役割】
 * 認証済みユーザーが、
 * 権限を持たない操作を行おうとした場合に使用する独自例外です。
 *
 * 例：
 * - 他ユーザーが投稿したTopicを編集しようとした場合
 *
 * REST APIではGlobalExceptionHandlerで受け取り、
 * 403 Forbiddenへ変換する想定です。
 */


public class ForbiddenOperationException extends RuntimeException {

    public ForbiddenOperationException(String message) {
        super(message);
    }
}