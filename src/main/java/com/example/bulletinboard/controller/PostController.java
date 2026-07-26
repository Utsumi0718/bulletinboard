package com.example.bulletinboard.controller;

import com.example.bulletinboard.model.Post;
import com.example.bulletinboard.service.PostService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Controller;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/*
 * 【クラスの役割】
 * ユーザーからの HTTP リクエストを受け取り、適切な画面表示やデータ処理を制御する Web コントローラークラスです。
 * `PostService` を呼び出して投稿データの取得や保存を行い、取得したデータを画面側（Thymeleaf 等のテンプレート）へ渡したり、
 * 処理完了後の画面遷移（リダイレクト等）の指示を出したりする役割を担います。
 */
@Controller//このクラスがコントローラであることを宣言する。
@RequestMapping("/posts")//このコントローラーがベースとなるURLパスを指定する。
public class PostController {
    private final PostService postService;

    public PostController(PostService postService) { //コンストラクタでPostServiceを注入（DI）インジェクションする。
        this.postService = postService;
    }

    //掲示板の一覧を表示
    @GetMapping
    public String ListPosts(Model model){
       List<Post> posts = postService.findAll();
       model.addAttribute("posts", posts); //画面に渡すデータをセット
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
    public String createPost(@Validated @ModelAttribute Post post, BindingResult bindingResult){
        // createPostメソッド: フォームから送信されたデータを @ModelAttribute で Post オブジェクトに自動マッピングして受け取る
        if (bindingResult.hasErrors()) {
        return "posts/new"; // エラーがあれば入力画面に戻る（これでテストの isOk() が通る）
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
        return "posts/detail"; // templates/posts/detail.html を表示
    }

    //編集画面の表示
    @GetMapping("/{id}/edit")
    public String editPostForm(@PathVariable Long id, Model model){
        Post post = postService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid post Id:" + id));
        model.addAttribute("post", post);
        return "posts/edit"; // templates/posts/edit.html を表示
    }

    //投稿の更新処理
    @PostMapping("/{id}")
    // @PathVariable: 対象のIDを取得 / @ModelAttribute: フォームから送信された入力値（タイトル・本文）を自動で Post オブジェクトにマッピング
    public String updatePost(@PathVariable Long id, @ModelAttribute Post post){
     // 既存のデータを取得して、内容を書き換えて保存する。
     Post existingPost = postService.findById(id)
    // データベースから「書き換える前の本物の投稿データ」を取得
            .orElseThrow(() -> new IllegalArgumentException("Invalid post Id:" + id));
    //対象データが存在しない場合は安全に例外を発生させる

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
    public String deletePost(@PathVariable Long id) {
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