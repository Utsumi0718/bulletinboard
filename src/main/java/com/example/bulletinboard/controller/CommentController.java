package com.example.bulletinboard.controller;

import com.example.bulletinboard.model.Comment;
import com.example.bulletinboard.model.Post;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.service.CommentService;
import com.example.bulletinboard.service.PostService;
import com.example.bulletinboard.service.CustomUserDetailsService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/*
* 【クラスの役割】
 * コメント機能に関するリクエストを処理するコントローラークラスです。
 * コメントの投稿や削除などの操作を受け付け、対応するサービス層（CommentService）を呼び出してビジネスロジックを実行します。
 * また、ログインユーザー情報を取得し、認可制御（自分が書いたコメントのみ削除可能）も行います。
*/

@Controller
@RequestMapping("/comments")
public class CommentController {
 private final CommentService commentService;
 private final PostService postService;
 protected final CustomUserDetailsService userDetailsService;
    // コンストラクタで CommentService、PostService、CustomUserDetailsService を注入（DI）インジェクションする
    public CommentController(CommentService commentService, PostService postService, CustomUserDetailsService userDetailsService) {
        this.commentService = commentService;
        this.postService = postService;
        this.userDetailsService = userDetailsService;
    }

    //コメント投稿の処理
    @PostMapping("/add")
    public String addComment(
        @RequestParam Long postId,
        @RequestParam String content,
        @AuthenticationPrincipal UserDetails userDetails) { //ログイン情報を受け取る

        //ログインユーザーの取得
        User currentUser = userDetailsService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("ユーザーが見つかりません"));

        //投稿対象のPostを取得
        Post post = postService.findById(postId)
               .orElseThrow(() -> new IllegalArgumentException("投稿が見つかりません"));

        //コメントオブジェクトの作成
        Comment comment = new Comment();
        comment.setPost(post);
        comment.setUser(currentUser);
        comment.setContent(content);

        //コメントの保存
        commentService.saveComment(comment);

        //リダイレクト先のURLを返す（投稿詳細ページにリダイレクト）
        return "redirect:/posts/" + postId;

        }

        //コメント削除の処理
        @PostMapping("/{id}/delete")
        public String deleteComment(@PathVariable Long id,
                                @RequestParam Long postId,
                                @AuthenticationPrincipal UserDetails userDetails){

            Comment comment = commentService.getCommentById(id)
                    .orElseThrow(() -> new IllegalArgumentException("コメントが見つかりません"));

         //認可制御：自分が書いたコメントかチェック
         if(comment.getUser() != null && comment.getUser().getUsername().equals(userDetails.getUsername())){
             commentService.deleteComment(id);
         }

          return "redirect:/posts/" + postId;
        }



}

