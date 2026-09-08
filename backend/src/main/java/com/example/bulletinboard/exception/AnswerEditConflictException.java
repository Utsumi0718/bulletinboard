package com.example.bulletinboard.exception;

/*
 * 【クラスの役割】
 * Answerの編集時に、
 * 業務ルール上の競合が発生した場合に使用する独自Exceptionです。
 *
 * 現在は、Likeが1件以上付いているAnswerを
 * 編集しようとした場合に使用します。
 *
 * REST APIではGlobalExceptionHandlerによって
 * 409 Conflictへ変換することを想定しています。
 */
public class AnswerEditConflictException extends RuntimeException {

    public AnswerEditConflictException(String message) {
        super(message);
    }
}