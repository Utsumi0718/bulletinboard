package com.example.bulletinboard.dto.like;

/**
 * Like操作後の状態を返すレスポンスDTO。
 *
 * 対象Answerに対する現在のLike状態と、
 * 最新のLike件数を保持する。
 */
public class LikeResponse {

    private final boolean liked;
    private final long likeCount;

    public LikeResponse(boolean liked, long likeCount) {
        this.liked = liked;
        this.likeCount = likeCount;
    }

    public boolean isLiked() {
        return liked;
    }

    public long getLikeCount() {
        return likeCount;
    }
}