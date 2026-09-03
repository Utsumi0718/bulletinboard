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

    /*
     * PostServiceではなくTopicServiceをDIします。
     */
    public TopicController(
            TopicService topicService,
            CustomUserDetailsService userDetailsService) {

        this.topicService = topicService;
        this.userDetailsService = userDetailsService;
    }

    /*
     * Post一覧ではなくTopic一覧を取得します。
     */
    @GetMapping
    public String listTopics(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            Model model) {

        Page<Topic> topicPage;

        /*
         * 検索対象はTopicのタイトルのみとし、
         * 部分一致検索を行います。
         */
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

        /*
         * PostではなくTopicをModelへ渡します。
         */
        model.addAttribute("topicPage", topicPage);
        model.addAttribute("topics", topicPage.getContent());
        model.addAttribute("page", page);
        model.addAttribute("keyword", keyword);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("sortOrder", sortOrder);

        /*
         * 旧Thymeleafテンプレートは現段階では維持します。
         */
        return "posts/list";
    }

    /*
     * 新規Postではなく新規TopicをModelへ渡し、
     * 新規投稿画面を表示します。
     */
    @GetMapping("/new")
    public String newTopicForm(Model model) {

        model.addAttribute("topic", new Topic());

        /*
         * 旧Thymeleafテンプレートは現段階では維持します。
         */
        return "posts/new";
    }

    /*
     * PostではなくTopicを新規作成します。
     */
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

        /*
         * ログインユーザーをTopicに紐付けます。
         *
         * PrincipalにはログインIDであるemailが設定されているため、
         * emailを基準にUserを取得します。
         */
        if (userDetails != null) {
            User currentUser = userDetailsService
                    .findByEmail(userDetails.getUsername())
                    .orElseThrow(() ->
                            new IllegalArgumentException("ユーザーが見つかりません"));

            topic.setUser(currentUser);
        }

        /*
         * PostServiceではなくTopicServiceを利用して保存します。
         */
        topicService.save(topic);

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "投稿に成功しました！");

        return "redirect:/posts";
    }

    /*
     * Post詳細ではなくTopic詳細を取得します。
     *
     * Comment / Like処理は後続フェーズで再実装します。
     */
    @GetMapping("/{id}")
    public String viewTopic(
            @PathVariable Long id,
            Model model) {

        Topic topic = topicService.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Invalid topic Id:" + id));

        model.addAttribute("topic", topic);

        /*
         * 旧Thymeleafテンプレートは現段階では維持します。
         */
        return "posts/detail";
    }

    /*
     * PostではなくTopicの編集画面を表示します。
     */
    @GetMapping("/{id}/edit")
    public String editTopicForm(
            @PathVariable Long id,
            Model model,
            @AuthenticationPrincipal UserDetails userDetails) {

        Topic topic = topicService.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Invalid topic Id:" + id));

        /*
         * PrincipalにはログインIDであるemailが設定されているため、
         * User.emailと比較して投稿者本人か判定します。
         */
        if (topic.getUser() == null
                || !topic.getUser()
                         .getEmail()
                         .equals(userDetails.getUsername())) {

            return "redirect:/posts";
        }

        model.addAttribute("topic", topic);

        /*
         * 旧Thymeleafテンプレートは現段階では維持します。
         */
        return "posts/edit";
    }

    /*
     * PostではなくTopicを更新します。
     */
    @PostMapping("/{id}")
    public String updateTopic(
            @PathVariable Long id,
            @ModelAttribute Topic topic,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        Topic existingTopic = topicService.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Invalid topic Id:" + id));

        /*
         * PrincipalにはログインIDであるemailが設定されているため、
         * User.emailと比較して投稿者本人か判定します。
         */
        if (existingTopic.getUser() == null
                || !existingTopic.getUser()
                                 .getEmail()
                                 .equals(userDetails.getUsername())) {

            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "投稿の更新に失敗しました。");

            return "redirect:/posts";
        }

        /*
         * 旧Postのcontentは廃止し、
         * Topicのtitle / questionを更新します。
         */
        existingTopic.setTitle(topic.getTitle());
        existingTopic.setQuestion(topic.getQuestion());

        /*
         * imageの更新仕様はtopic-answer-apiで対応します。
         */
        topicService.save(existingTopic);

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "投稿の更新に成功しました！");

        return "redirect:/posts";
    }

    /*
     * 指定されたTopicを論理削除します。
     *
     * Controllerではログインユーザーのemailと
     * ROLE_ADMIN権限の有無を取得し、
     * 削除可否の業務ルール判定はTopicServiceへ委譲します。
     */
    @PostMapping("/{id}/delete")
    public String deleteTopic(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        boolean isAdmin = userDetails.getAuthorities()
                .stream()
                .anyMatch(a ->
                        a.getAuthority().equals("ROLE_ADMIN"));

        topicService.deleteById(
                id,
                userDetails.getUsername(),
                isAdmin
        );

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "お題の削除に成功しました！");

        return "redirect:/posts";
    }
}