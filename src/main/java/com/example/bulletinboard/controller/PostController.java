package com.example.bulletinboard.controller;

import com.example.bulletinboard.model.Post;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.service.PostService;
import com.example.bulletinboard.service.CustomUserDetailsService;
import com.example.bulletinboard.service.CommentService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Controller;
import java.util.List;

/*
 * 【クラスの役割】
 * ユーザーからの HTTP リクエストを受け取り、適切な画面表示やデータ処理を制御する Web コントローラークラスです。
 * `PostService` を呼び出して投稿データの取得や保存を行い、取得したデータを画面側（Thymeleaf 等のテンプレート）へ渡したり、
 * 処理完了後の画面遷移（リダイレクト等）の指示を出したりする役割を担います。
 *[追記]：「投稿時にユーザー情報を関連付ける処理」と「編集・削除時に本人かどうかの認可チェック」
 *[追記]：listPostsメソッド内に検索機能を追加
 *[追記]：画面のフォームから送信される並び替え用のURLパラメータ（sortBy と sortOrder）をコントローラーで受け取り、
 *サービス層（PostService）への受け渡しおよび画面（Model）への返却を行う
 */
@Controller//このクラスがコントローラであることを宣言する。
@RequestMapping("/posts")//このコントローラーがベースとなるURLパスを指定する。
public class PostController {
    private final PostService postService;
    private final CustomUserDetailsService userDetailsService; //ユーザー専用のサービス
    private final CommentService commentService; //コメント専用のサービス

    public PostController(PostService postService, CustomUserDetailsService userDetailsService, CommentService commentService) { //コンストラクタでPostServiceとCustomUserDetailsServiceを注入（DI）インジェクションする。
        this.postService = postService;
        this.userDetailsService = userDetailsService;
        this.commentService = commentService;
    }

    //掲示板の一覧を表示
    @GetMapping
    public String listPosts(@RequestParam(required = false) String keyword,
                            @RequestParam(required = false) String matchType,
                            @RequestParam(required = false) String sortBy,
                            @RequestParam(required = false) String sortOrder,
                            Model model){
       List<Post> posts;

       //検索キーワードが指定されている場合は検索結果をソートして取得
       if(keyword != null && !keyword.isEmpty()){
         posts = postService.searchPosts(keyword, matchType,sortBy,sortOrder);
       }else{
         posts = postService.findAll(sortBy,sortOrder);
       }

        model.addAttribute("posts", posts); //画面に渡すデータをセット

        //画面側でキーワードと一致条件を保持するためにModelに追加
        model.addAttribute("keyword", keyword);
        model.addAttribute("matchType", matchType);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("sortOrder", sortOrder);

        return "posts/list"; // templates/posts/list.html を表示
    }

    //新規投稿フォームの表示
    @GetMapping("/new")
    public String newPostForm(Model model){
    model.addAttribute("post", new Post());
    return "posts/new"; // templates/posts/new.html を表示
    }

    //新規投稿の保存の処理
    @PostMapping
    public String createPost(
        @Validated @ModelAttribute Post post,
        BindingResult bindingResult,
        @AuthenticationPrincipal UserDetails userDetails){ //追記：ログイン情報を受け取る

    // createPostメソッド: フォームから送信されたデータを @ModelAttribute で Post オブジェクトに自動マッピングして受け取る
        if (bindingResult.hasErrors()) {
        return "posts/new"; // エラーがあれば入力画面に戻る（これでテストの isOk() が通る）
    }

    //追記：ログインユーザーを取得してPostにセットする処理を追加
     if(userDetails != null){
       User currentUser = userDetailsService.findByUsername(userDetails.getUsername())
                          .orElseThrow(() -> new IllegalArgumentException("ユーザーが見つかりません"));
            post.setUser(currentUser); // 👈 このセット処理を追加！
     }
        postService.save(post);
        return "redirect:/posts"; //投稿後、掲示板一覧にリダイレクト
    }

    //投稿の詳細表示
    @GetMapping("/{id}")// @GetMapping("/{id}"): /posts/{id} 形式の HTTP GET リクエストに対応付け（{id} は可変の値）
    public String viewPost(@PathVariable Long id, Model model){
    // @PathVariable: URL パスの {id} 部分を引数 Long id に割り当て。Model は画面へ渡すデータの格納用
      Post post = postService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid post Id:" + id));
     // データが存在すれば Post を取り出し、存在しなければ例外（IllegalArgumentException）を発生させる
       model.addAttribute("post", post);

       model.addAttribute("comments", commentService.getCommentsByPostId(id)); // コメント一覧を取得して画面に渡す

        return "posts/detail"; // templates/posts/detail.html を表示
    }

    //編集画面の表示
    @GetMapping("/{id}/edit")
    public String editPostForm(@PathVariable Long id, Model model, @AuthenticationPrincipal UserDetails userDetails){
        Post post = postService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid post Id:" + id));

     //追記：本人チェック（投稿者がいない、または別のユーザーの場合はアクセス拒否をして一覧へ）
     if(post.getUser() == null || !post.getUser().getUsername().equals(userDetails.getUsername())){
       return "redirect:/posts";
     }

         model.addAttribute("post", post);
        return "posts/edit"; // templates/posts/edit.html を表示
    }

    //投稿の更新処理
    @PostMapping("/{id}")
    // @PathVariable: 対象のIDを取得 / @ModelAttribute: フォームから送信された入力値（タイトル・本文）を自動で Post オブジェクトにマッピング
    public String updatePost(@PathVariable Long id, @ModelAttribute Post post, @AuthenticationPrincipal UserDetails userDetails){
     // 既存のデータを取得して、内容を書き換えて保存する。
     Post existingPost = postService.findById(id)
    // データベースから「書き換える前の本物の投稿データ」を取得
            .orElseThrow(() -> new IllegalArgumentException("Invalid post Id:" + id));
    //対象データが存在しない場合は安全に例外を発生させる

    //追記：本人チェック
    if (existingPost.getUser() == null || !existingPost.getUser().getUsername().equals(userDetails.getUsername())) {
            return "redirect:/posts";
        }

    existingPost.setTitle(post.getTitle());
    // 画面から送られてきた新しいタイトル（post.getTitle()）で、既存データ（existingPost）のタイトルを上書き

    existingPost.setContent(post.getContent());
    // 画面から送られてきた新しい本文（post.getContent()）で、既存データ（existingPost）の本文を上書き

    postService.save(existingPost);
    //内容を上書きした既存データ（existingPost）をデータベースに保存（UPDATE文が発行される）

    return "redirect:/posts"; // 更新後は一覧へ
    //  更新完了後、ブラウザに対して投稿一覧画面（/posts）へリダイレクト（転送）指示を出す
    }

    //deleteメソッドの二重送信を防ぐ

    @PostMapping("/{id}/delete")
    public String deletePost(@PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
       //追記例外
       Post post = postService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid post Id:" + id));

       // 追記：本人チェック
        if (post.getUser() == null || !post.getUser().getUsername().equals(userDetails.getUsername())) {
            return "redirect:/posts";
        }

        postService.deleteById(id);
       //直接ビューを返さず完了画面へリダイレクトさせる
        return "redirect:/posts/delete-complete";//削除後削除完了画面を表示
    }

    //削除完了画面を表示
    @GetMapping("/delete-complete")
    public String showDeleteComplete() {
        return "posts/deleteComplete";
    }


}