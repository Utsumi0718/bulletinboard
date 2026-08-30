package com.example.bulletinboard.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.example.bulletinboard.model.Answer;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.service.AnswerService;
import com.example.bulletinboard.service.CustomUserDetailsService;
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
    private final AnswerService answerService;
    private final CustomUserDetailsService userDetailsService;

    public LikeController(
            LikeService likeService,
            AnswerService answerService,
            CustomUserDetailsService userDetailsService) {

        this.likeService = likeService;
        this.answerService = answerService;
        this.userDetailsService = userDetailsService;
    }

    @PostMapping("/{answerId}/like")
    public ResponseEntity<Map<String, Object>> toggleLike(
            @PathVariable Long answerId,
            @AuthenticationPrincipal UserDetails userDetails) {

        // ログインユーザーの取得
        // email認証との正式な整合はauth-account-refactorで対応する
        User user = userDetailsService.findByUsername(userDetails.getUsername())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "ユーザーが見つかりません: " + userDetails.getUsername()
                        ));

        // 変更：PostではなくAnswerを取得する
        Answer answer = answerService.getAnswerById(answerId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "指定された回答は見つかりません: " + answerId
                        ));

        // 変更：Answerに対するいいねの切り替えを実行
        boolean isLiked = likeService.toggleLike(user, answer);

        // 変更：Answerの最新いいね件数を取得
        long likeCount = likeService.getLikeCount(answer);

        // レスポンスデータの組み立て
        Map<String, Object> response = new HashMap<>();
        response.put("liked", isLiked);
        response.put("count", likeCount);

        // JSONデータとして返却
        return ResponseEntity.ok(response);
    }
}