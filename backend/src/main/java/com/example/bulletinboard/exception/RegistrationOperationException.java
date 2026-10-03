package com.example.bulletinboard.exception;

/** 登録処理のDB・トランザクション障害を安全な500応答へ変換するための例外。 */
public class RegistrationOperationException extends RuntimeException {
    public RegistrationOperationException(Throwable cause) {
        super("registration_failed", cause);
    }
}
