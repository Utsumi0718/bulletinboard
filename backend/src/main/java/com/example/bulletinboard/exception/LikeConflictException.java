package com.example.bulletinboard.exception;

/**
 * Like登録時に競合が発生した場合の例外。
 *
 * 同一User + Answerへの重複Likeなど、
 * 現在のLike状態と登録処理が競合した場合に使用する。
 */
public class LikeConflictException extends RuntimeException {

    public LikeConflictException(String message) {
        super(message);
    }
}