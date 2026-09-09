package com.example.bulletinboard.exception;

/**
 * 指定されたTopicが存在しない、
 * または論理削除済みの場合に使用する例外です。
 */
public class TopicNotFoundException extends RuntimeException {

    public TopicNotFoundException(String message) {
        super(message);
    }
}