package com.example.bulletinboard.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;//追加

import com.example.bulletinboard.model.Comment;
import com.example.bulletinboard.model.Post;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.service.CommentService;
import com.example.bulletinboard.service.CustomUserDetailsService;
import com.example.bulletinboard.service.PostService;

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
        @AuthenticationPrincipal UserDetails userDetails,
        RedirectAttributes redirectAttributes) { //ログイン情報を受け取る

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

        //追記：コメントのフラッシュメッセージ
        redirectAttributes.addFlashAttribute("successMessage", userDetails.getUsername() + "さんのコメントが投稿されました！");

        //リダイレクト先のURLを返す（投稿詳細ページにリダイレクト）
        return "redirect:/posts/" + postId;

        }

        //コメント削除の処理
        @PostMapping("/{id}/delete")
        public String deleteComment(@PathVariable Long id,
                                @RequestParam Long postId,
                                @AuthenticationPrincipal UserDetails userDetails,
                                RedirectAttributes redirectAttributes){

            Comment comment = commentService.getCommentById(id)
                    .orElseThrow(() -> new IllegalArgumentException("コメントが見つかりません"));

          //追記：本人および管理者チェック
          boolean isLoginUser = comment.getUser() != null && comment.getUser().getUsername().equals(userDetails.getUsername());
          boolean isAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));


         //認可制御：自分か管理者かチェック
         if(isLoginUser || isAdmin){
             commentService.deleteComment(id);
            redirectAttributes.addFlashAttribute("successMessage", userDetails.getUsername() + "さんのコメントが削除されました！");
          } else {
            // 💡 本人でない場合はエラーメッセージをセット
            redirectAttributes.addFlashAttribute("errorMessage", "コメントの削除権限がありません。");
          }



          return "redirect:/posts/" + postId;
        }





}

