package com.example.bulletinboard.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;//追加


import com.example.bulletinboard.model.Post;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.service.CommentService;
import com.example.bulletinboard.service.CustomUserDetailsService;
import com.example.bulletinboard.service.LikeService;
import com.example.bulletinboard.service.PostService;

/*
 * 【クラスの役割】
 * ユーザーからの HTTP リクエストを受け取り、適切な画面表示やデータ処理を制御する Web コントローラークラスです。
 * `PostService` を呼び出して投稿データの取得や保存を行い、取得したデータを画面側（Thymeleaf 等のテンプレート）へ渡したり、
 * 処理完了後の画面遷移（リダイレクト等）の指示を出したりする役割を担います。
 * [追記]：「投稿時にユーザー情報を関連付ける処理」と「編集・削除時に本人かどうかの認可チェック」
 * [追記]：listPostsメソッド内に検索機能を追加
 * [追記]：画面のフォームから送信される並び替え用のURLパラメータ（sortBy と sortOrder）をコントローラーで受け取り、
 * サービス層（PostService）への受け渡しおよび画面（Model）への返却を行う
 * [追記]：Service から返ってきた Page<Post> を Model に渡す
 * [追記]：投稿一覧および詳細表示時に「いいね件数」と「ログインユーザーのいいね状態」を Model に渡す処理を追加
 */
@Controller // このクラスがコントローラであることを宣言する。
@RequestMapping("/posts") // このコントローラーがベースとなるURLパスを指定する。
public class PostController {

    private final PostService postService;
    private final CustomUserDetailsService userDetailsService; // ユーザー専用のサービス
    private final CommentService commentService; // コメント専用のサービス
    private final LikeService likeService; // 【追加】いいね専用のサービス

    // コンストラクタで依存オブジェクトを注入（DI）
    public PostController(PostService postService,
                          CustomUserDetailsService userDetailsService,
                          CommentService commentService,
                          LikeService likeService) {
        this.postService = postService;
        this.userDetailsService = userDetailsService;
        this.commentService = commentService;
        this.likeService = likeService;
    }

    // 掲示板の一覧を表示
    @GetMapping
    public String listPosts(@RequestParam(defaultValue = "0") int page,
                            @RequestParam(required = false) String keyword,
                            @RequestParam(required = false) String matchType,
                            @RequestParam(required = false) String sortBy,
                            @RequestParam(required = false) String sortOrder,
                            @AuthenticationPrincipal UserDetails userDetails, // 【追加】ログイン情報を取得
                            Model model) {
        Page<Post> postPage;

        // 検索キーワードが指定されている場合は検索結果をソートして取得
        if (keyword != null && !keyword.isEmpty()) {
            postPage = postService.searchPosts(page, keyword, matchType, sortBy, sortOrder);
        } else {
            postPage = postService.findAll(page, sortBy, sortOrder);
        }

        // ログインユーザーの取得
        User currentUser = null;
        if (userDetails != null) {
            currentUser = userDetailsService.findByUsername(userDetails.getUsername()).orElse(null);
        }

        // 各投稿のいいね数とログインユーザーのいいね状態を保持するMapを作成
        Map<Long, Long> likeCounts = new HashMap<>();
        Map<Long, Boolean> isLikedMap = new HashMap<>();

        for (Post post : postPage.getContent()) {
            likeCounts.put(post.getId(), likeService.getLikeCount(post));
            isLikedMap.put(post.getId(), likeService.isLikedByUser(currentUser, post));
        }

        model.addAttribute("postPage", postPage); // ページ情報を保持したオブジェクトを画面へ
        model.addAttribute("posts", postPage.getContent()); // 既存の HTML (th:each="post : ${posts}") がそのまま動くようにリストもセット

        // 【追加】いいね情報を画面へ渡す
        model.addAttribute("likeCounts", likeCounts);
        model.addAttribute("isLikedMap", isLikedMap);

        // 画面側でキーワードと一致条件を保持するためにModelに追加
        model.addAttribute("page", page);
        model.addAttribute("keyword", keyword);
        model.addAttribute("matchType", matchType);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("sortOrder", sortOrder);

        return "posts/list"; // templates/posts/list.html を表示
    }

    // 新規投稿フォームの表示
    @GetMapping("/new")
    public String newPostForm(Model model) {
        model.addAttribute("post", new Post());
        return "posts/new"; // templates/posts/new.html を表示
    }

    // 新規投稿の保存の処理
    @PostMapping
    public String createPost(
            @Validated @ModelAttribute Post post,
            BindingResult bindingResult,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes,// 追加：セッションを介して一時的に値を保持
            Model model) {

        if (bindingResult.hasErrors()) {
            //追記：Modelでエラーメッセージを設定（同じテンプレートで返す）
            model.addAttribute("errorMessage","投稿に失敗しました。");
            return "posts/new"; // エラーがあれば入力画面に戻る
        }

        // ログインユーザーを取得してPostにセットする処理
        if (userDetails != null) {
            User currentUser = userDetailsService.findByUsername(userDetails.getUsername())
                    .orElseThrow(() -> new IllegalArgumentException("ユーザーが見つかりません"));
            post.setUser(currentUser);
        }
        postService.save(post);

        //追記：リダイレクト先にフラッシュメッセージをセット
        redirectAttributes.addFlashAttribute("successMessage","投稿に成功しました！");

        return "redirect:/posts"; // 投稿後、掲示板一覧にリダイレクト
    }

    // 投稿の詳細表示
    @GetMapping("/{id}")
    public String viewPost(@PathVariable Long id,
                           @AuthenticationPrincipal UserDetails userDetails, // 【追加】ログイン情報を取得
                           Model model) {
        Post post = postService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid post Id:" + id));

        // ログインユーザーの取得
        User currentUser = null;
        if (userDetails != null) {
            currentUser = userDetailsService.findByUsername(userDetails.getUsername()).orElse(null);
        }

        // いいね件数とログインユーザーがいいね済みかを判定
        long likeCount = likeService.getLikeCount(post);
        boolean isLiked = likeService.isLikedByUser(currentUser, post);

        model.addAttribute("post", post);
        model.addAttribute("comments", commentService.getCommentsByPostId(id)); // コメント一覧を取得して画面に渡す

        // 【追加】いいね情報を画面へ渡す
        model.addAttribute("likeCount", likeCount);
        model.addAttribute("isLiked", isLiked);

        return "posts/detail"; // templates/posts/detail.html を表示
    }

    // 編集画面の表示
    @GetMapping("/{id}/edit")
    public String editPostForm(@PathVariable Long id, Model model, @AuthenticationPrincipal UserDetails userDetails) {
        Post post = postService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid post Id:" + id));

        // 本人チェック（投稿者がいない、または別のユーザーの場合はアクセス拒否をして一覧へ）
        if (post.getUser() == null || !post.getUser().getUsername().equals(userDetails.getUsername())) {
            return "redirect:/posts";
        }

        model.addAttribute("post", post);
        return "posts/edit"; // templates/posts/edit.html を表示
    }

    // 投稿の更新処理
    @PostMapping("/{id}")
    public String updatePost(@PathVariable Long id,
                             @ModelAttribute Post post,
                             @AuthenticationPrincipal UserDetails userDetails,
                             RedirectAttributes redirectAttributes,
                             Model model) {

        Post existingPost = postService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid post Id:" + id));

        // 本人チェック

        // 本人チェック
        if (existingPost.getUser() == null || !existingPost.getUser().getUsername().equals(userDetails.getUsername())) {
            model.addAttribute("errorMessage", "投稿の更新に失敗しました。");
            return "redirect:/posts";
        }

        existingPost.setTitle(post.getTitle());
        existingPost.setContent(post.getContent());

        postService.save(existingPost);

        //追記：リダイレクト先にフラッシュメッセージをセット
        redirectAttributes.addFlashAttribute("successMessage","投稿の更新に成功しました！");

        return "redirect:/posts"; // 更新後は一覧へ
    }

    // 投稿の削除処理
    @PostMapping("/{id}/delete")
    public String deletePost(@PathVariable Long id,
                             @AuthenticationPrincipal UserDetails userDetails,
                             RedirectAttributes redirectAttributes,
                             Model model) {
        Post post = postService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid post Id:" + id));

        // 本人チェック
        if (post.getUser() == null || !post.getUser().getUsername().equals(userDetails.getUsername())) {
            model.addAttribute("errorMessage", "投稿の削除に失敗しました。");
            return "redirect:/posts";
        }

        postService.deleteById(id);

        //追記：リダイレクト先にフラッシュメッセージをセット
        redirectAttributes.addFlashAttribute("successMessage","投稿の削除に成功しました！");

        return "redirect:/posts"; //追記：投稿削除後は投稿一覧画面へリダイレクト
    }


}