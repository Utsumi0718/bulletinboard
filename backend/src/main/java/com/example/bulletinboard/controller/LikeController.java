package com.example.bulletinboard.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;//追加：HTTPレスポンスの全体（ステータスコード、ヘッダー、ボディ）を細かく制御して返却するためのクラスを読み込むため
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


/**
 * 【クラスの役割】
 * ユーザーから「いいね」登録・解除リクエスト受け取るコントローラークラス
 * ログインユーザー情報を取得して、LikeServiceを介してトグル処理を実行後、元の画面へリダイレクトする
 *[追記]：画面遷移（HTML返却）から JSONデータを返す処理 へ修正
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
public ResponseEntity<Map<String, Object>> toggleLike(
       @PathVariable Long postId,
       @AuthenticationPrincipal UserDetails userDetails){

      //ログインユーザーの取得
      User user = userDetailsService.findByUsername(userDetails.getUsername())
                  .orElseThrow(()-> new IllegalArgumentException("ユーザーが見つかりません" +  userDetails.getUsername()));

      Post post = postService.findById(postId)
                  .orElseThrow(() -> new IllegalArgumentException("指定された投稿は見つかりません" + postId));

      //「いいね」の切り替えを実行
      boolean isLiked =  likeService.toggleLike(user, post);

      // 最新のいいね件数を取得
      long likeCount = likeService.getLikeCount(post);


      //追記：レスポンスデータの組み立て
      Map<String,Object> response = new HashMap<>();
      response.put("liked", isLiked);
      response.put("count", likeCount);

      //追記：最終的な出力の決定（JSONデータとして返却）


      return ResponseEntity.ok(response);
}

}