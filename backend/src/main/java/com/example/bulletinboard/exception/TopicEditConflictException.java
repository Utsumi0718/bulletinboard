package com.example.bulletinboard.exception;

/**
 * Topic編集時に、
 * 業務ルール上の競合によって編集できない場合に使用する独自例外です。
 *
 * 例：
 * - Answerが一度でも投稿されたTopicを編集しようとした場合
 *
 * REST APIではGlobalExceptionHandlerで受け取り、
 * 409 Conflictへ変換する想定です。
 */
public class TopicEditConflictException extends RuntimeException {

    public TopicEditConflictException(String message) {
        super(message);
    }
}