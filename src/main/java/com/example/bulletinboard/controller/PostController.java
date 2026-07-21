package com.example.bulletinboard.controller;

import com.example.bulletinboard.model.Post;
import com.example.bulletinboard.service.PostService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Controller;
import java.util.List;

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
       List<Post> posts = postService.getAllPosts();
       model.addAttribute("posts", posts); //画面に渡すデータをセット
        return "post/list"; // templates/posts/list.html を表示
    }

    //新規投稿フォームの表示
    @GetMapping("/new")
    public String newPostForm(Model model){
    model.addAttribute("post", new Post());
    return "post/new"; // templates/posts/new.html を表示
    }

    //新規投稿の保存の処理
    public String createPost(@ModelAttribute Post post){
        // createPostメソッド: フォームから送信されたデータを @ModelAttribute で Post オブジェクトに自動マッピングして受け取る
        postService.savePost(post);
        return "redirect:/posts"; //投稿後、掲示板一覧にリダイレクト
    }
}