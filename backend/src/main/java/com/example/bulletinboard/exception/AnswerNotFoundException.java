package com.example.bulletinboard.exception;

/*
 * 【クラスの役割】
 * 指定されたAnswerが存在しない、
 * または論理削除済みの場合に使用する独自Exceptionです。
 *
 * REST APIではGlobalExceptionHandlerによって
 * 404 Not Foundへ変換することを想定しています。
 */
public class AnswerNotFoundException extends RuntimeException {

    public AnswerNotFoundException(String message) {
        super(message);
    }
}