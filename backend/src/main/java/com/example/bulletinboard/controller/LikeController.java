package com.example.bulletinboard.controller;


import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.example.bulletinboard.dto.like.LikeResponse;
import com.example.bulletinboard.service.LikeService;

/**
 * 【クラスの役割】
 * 回答（Answer）への「いいね」登録・解除リクエストを受け取るControllerです。
 *
 * ログインユーザー情報を取得し、
 * AnswerServiceを利用して対象となるAnswerを取得したうえで、
 * LikeServiceを介していいねのトグル処理を実行します。
 *
 * 処理後は、現在のいいね状態といいね件数をJSON形式で返却します。
 *
 * このクラスは旧Postへのいいね処理を、
 * Answerへのいいね仕様へ移行する途中段階のControllerです。
 *
 * 自分の回答へのいいね禁止・削除済みAnswerへのいいね禁止・
 * 重複制御・最終的なAPI仕様は、
 * 後続のfeature/like-featureで対応します。
 */
@Controller
@RequestMapping("/answers")
public class LikeController {

    private final LikeService likeService;


    public LikeController(
            LikeService likeService) {

        this.likeService = likeService;
    }

    @PostMapping("/{answerId}/like")
 public ResponseEntity<LikeResponse> toggleLike(
        @PathVariable Long answerId,
        @AuthenticationPrincipal UserDetails userDetails) {

    LikeResponse response = likeService.toggleLike(
            answerId,
            userDetails.getUsername()
    );

    return ResponseEntity.ok(response);
}
}