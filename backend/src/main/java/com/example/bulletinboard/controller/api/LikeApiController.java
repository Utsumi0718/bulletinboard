package com.example.bulletinboard.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.bulletinboard.dto.like.LikeResponse;
import com.example.bulletinboard.service.LikeService;


/**
 * 【クラスの役割】
 * Answerに対するLike操作を提供するREST API Controller。
 *
 * ログインユーザーの認証情報と対象AnswerのIDを受け取り、
 * LikeServiceへLikeの追加・解除処理を委譲します。
 *
 * ControllerではLikeに関する業務判定を行わず、
 * LikeServiceから返されたLikeResponseを
 * JSON形式でそのまま返します。
 */
@RestController
@RequestMapping("/api")
public class LikeApiController {

    private final LikeService likeService;

    public LikeApiController(LikeService likeService) {
        this.likeService = likeService;
    }

    /**
     * 指定されたAnswerに対するLike状態を切り替える。
     *
     * 未Likeの場合はLikeを追加し、
     * Like済みの場合はLikeを解除します。
     *
     * @param answerId   対象AnswerのID
     * @param userDetails ログインユーザーの認証情報
     * @return toggle後のliked状態と最新Like件数
     */
    @PostMapping("/answers/{answerId}/like")
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