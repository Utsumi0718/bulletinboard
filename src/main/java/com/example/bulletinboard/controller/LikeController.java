package com.example.bulletinboard.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.example.bulletinboard.model.Post;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.service.LikeService;
import com.example.bulletinboard.service.PostService;
import com.example.bulletinboard.service.CustomUserDetailsService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 【クラスの役割】
 * ユーザーから「いいね」登録・解除リクエスト受け取るコントローラークラス
 * ログインユーザー情報を取得して、LikeServiceを介してトグル処理を実行後、元の画面へリダイレクトする
 *
 */
@Controller
@RequestMapping("/posts") // 【修正：クラスレベルに /posts を追加】
public class LikeController{

private final LikeService likeService;
private final PostService postService;
private final CustomUserDetailsService userDetailsService;

public LikeController( LikeService likeService,
                       PostService postService,
                       CustomUserDetailsService userDetailsService
                     ){
     this.likeService = likeService;
     this.postService = postService;
     this.userDetailsService = userDetailsService;

}

@PostMapping("/{postId}/like")
public String toggleLike(
       @PathVariable Long postId,
       @AuthenticationPrincipal UserDetails userDetails,
       HttpServletRequest request){

      //ログインユーザーの取得
      User user = userDetailsService.findByUsername(userDetails.getUsername())
                  .orElseThrow(()-> new IllegalArgumentException("ユーザーが見つかりません" +  userDetails.getUsername()));

      Post post = postService.findById(postId)
                  .orElseThrow(() -> new IllegalArgumentException("指定された投稿は見つかりません" + postId));

      //「いいね」の切り替えを実行
      likeService.toggleLike(user, post);

      //リクエストの元の画面へリダイレクト
      String referer = request.getHeader("Referer");

      return "redirect:" + (referer != null ? referer : "/posts/" + postId);
}

}