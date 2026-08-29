package com.example.bulletinboard.controller;

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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.bulletinboard.model.Topic;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.service.CustomUserDetailsService;
import com.example.bulletinboard.service.TopicService;

/*
 * 【クラスの役割】
 * お題（Topic）に関するHTTPリクエストを受け取り、
 * お題の一覧表示・新規作成・詳細表示・編集・削除などの処理を制御するControllerです。
 *
 * TopicServiceを利用してTopicデータを取得・保存し、
 * 必要なデータを画面側へ渡したり、処理完了後の画面遷移を制御します。
 *
 * このクラスは旧PostControllerをTopic仕様へ移行する途中段階のControllerです。
 * AnswerやLikeに関する処理は、それぞれ後続の機能フェーズで分離・再実装します。
 */
@Controller
@RequestMapping("/posts")
public class TopicController {

    private final TopicService topicService;
    private final CustomUserDetailsService userDetailsService;

    // 変更：PostServiceではなくTopicServiceをDIする
    public TopicController(
            TopicService topicService,
            CustomUserDetailsService userDetailsService) {

        this.topicService = topicService;
        this.userDetailsService = userDetailsService;
    }

    // 変更：Post一覧ではなくTopic一覧を取得する
    @GetMapping
    public String listTopics(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            Model model) {

        Page<Topic> topicPage;

        // 変更：検索対象はTopicのタイトル部分一致のみ
        if (keyword != null && !keyword.isEmpty()) {
            topicPage = topicService.searchTopics(
                    page,
                    keyword,
                    sortBy,
                    sortOrder);
        } else {
            topicPage = topicService.findAll(
                    page,
                    sortBy,
                    sortOrder);
        }

        // 変更：PostではなくTopicをModelへ渡す
        model.addAttribute("topicPage", topicPage);
        model.addAttribute("topics", topicPage.getContent());
        model.addAttribute("page", page);
        model.addAttribute("keyword", keyword);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("sortOrder", sortOrder);

        // 旧Thymeleafテンプレートは現段階では維持
        return "posts/list";
    }

    // 変更：新規Postではなく新規TopicをModelへ渡す
    @GetMapping("/new")
    public String newTopicForm(Model model) {

        model.addAttribute("topic", new Topic());

        // 旧Thymeleafテンプレートは現段階では維持
        return "posts/new";
    }

    // 変更：PostではなくTopicを新規作成する
    @PostMapping
    public String createTopic(
            @Validated @ModelAttribute Topic topic,
            BindingResult bindingResult,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("errorMessage", "投稿に失敗しました。");
            return "posts/new";
        }

        // ログインユーザーをTopicに紐付ける
        // email認証との正式な整合はauth-account-refactorで対応する
        if (userDetails != null) {
            User currentUser = userDetailsService
                    .findByUsername(userDetails.getUsername())
                    .orElseThrow(() ->
                            new IllegalArgumentException("ユーザーが見つかりません"));

            topic.setUser(currentUser);
        }

        // 変更：PostServiceではなくTopicServiceで保存する
        topicService.save(topic);

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "投稿に成功しました！");

        return "redirect:/posts";
    }

    // 変更：Post詳細ではなくTopic詳細を取得する
    // Comment / Like処理は後続フェーズで再実装する
    @GetMapping("/{id}")
    public String viewTopic(
            @PathVariable Long id,
            Model model) {

        Topic topic = topicService.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Invalid topic Id:" + id));

        model.addAttribute("topic", topic);

        // 旧Thymeleafテンプレートは現段階では維持
        return "posts/detail";
    }

    // 変更：PostではなくTopicの編集画面を表示する
    @GetMapping("/{id}/edit")
    public String editTopicForm(
            @PathVariable Long id,
            Model model,
            @AuthenticationPrincipal UserDetails userDetails) {

        Topic topic = topicService.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Invalid topic Id:" + id));

        // 投稿者本人かチェック
        // email認証との正式な整合はauth-account-refactorで対応する
        if (topic.getUser() == null
                || !topic.getUser()
                         .getUsername()
                         .equals(userDetails.getUsername())) {

            return "redirect:/posts";
        }

        model.addAttribute("topic", topic);

        // 旧Thymeleafテンプレートは現段階では維持
        return "posts/edit";
    }

    // 変更：PostではなくTopicを更新する
    @PostMapping("/{id}")
    public String updateTopic(
            @PathVariable Long id,
            @ModelAttribute Topic topic,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        Topic existingTopic = topicService.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Invalid topic Id:" + id));

        // 投稿者本人かチェック
        // email認証との正式な整合はauth-account-refactorで対応する
        if (existingTopic.getUser() == null
                || !existingTopic.getUser()
                                 .getUsername()
                                 .equals(userDetails.getUsername())) {

            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "投稿の更新に失敗しました。");

            return "redirect:/posts";
        }

        // 変更：旧Postのcontentは廃止し、
        // Topicのtitle / questionを更新する
        existingTopic.setTitle(topic.getTitle());
        existingTopic.setQuestion(topic.getQuestion());

        // imageの更新仕様はtopic-answer-apiで対応する

        topicService.save(existingTopic);

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "投稿の更新に成功しました！");

        return "redirect:/posts";
    }

    // 変更：PostではなくTopicを論理削除する
    @PostMapping("/{id}/delete")
    public String deleteTopic(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        Topic topic = topicService.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Invalid topic Id:" + id));

        // ログインユーザー本人かチェック
        // email認証との正式な整合はauth-account-refactorで対応する
        boolean isLoginUser =
                topic.getUser() != null
                        && topic.getUser()
                                .getUsername()
                                .equals(userDetails.getUsername());

        // 管理者権限を持っているかチェック
        boolean isAdmin = userDetails.getAuthorities()
                .stream()
                .anyMatch(a ->
                        a.getAuthority().equals("ROLE_ADMIN"));

        if (!isLoginUser && !isAdmin) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "お題の削除権限がありません。");

            return "redirect:/posts";
        }

        // TopicService側でdeletedAtを設定する論理削除
        topicService.deleteById(id);

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "お題の削除に成功しました！");

        return "redirect:/posts";
    }
}