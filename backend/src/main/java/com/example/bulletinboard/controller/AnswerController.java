package com.example.bulletinboard.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.bulletinboard.model.Answer;
import com.example.bulletinboard.model.Topic;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.service.AnswerService;
import com.example.bulletinboard.service.CustomUserDetailsService;
import com.example.bulletinboard.service.TopicService;

/*
 * 【クラスの役割】
 * 回答（Answer）に関するHTTPリクエストを受け取り、
 * 回答の投稿・削除などの処理を制御するControllerです。
 *
 * AnswerServiceを利用してAnswerデータを保存・削除し、
 * TopicServiceを利用して回答対象となるTopicを取得します。
 *
 * また、ログインユーザー情報を取得し、
 * 回答者本人または管理者による削除権限の確認を行います。
 *
 * このクラスは旧CommentControllerをAnswer仕様へ移行する途中段階のControllerです。
 * Answerの詳細なバリデーション・編集条件・REST API化は、
 * 後続のfeature/topic-answer-apiで対応します。
 */
@Controller
@RequestMapping("/comments")
public class AnswerController {

    private final AnswerService answerService;
    private final TopicService topicService;
    private final CustomUserDetailsService userDetailsService;

    // 変更：AnswerService、TopicService、CustomUserDetailsServiceをDIする
    public AnswerController(
            AnswerService answerService,
            TopicService topicService,
            CustomUserDetailsService userDetailsService) {

        this.answerService = answerService;
        this.topicService = topicService;
        this.userDetailsService = userDetailsService;
    }

    // 変更：CommentではなくAnswerを投稿する
    @PostMapping("/add")
    public String addAnswer(
            @RequestParam Long topicId,
            @RequestParam String content,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        // PrincipalにはログインIDであるemailが設定されているため、
        // emailを基準にログインユーザーを取得する

         User currentUser = userDetailsService
            .findByEmail(userDetails.getUsername())
            .orElseThrow(() ->
                new IllegalArgumentException("ユーザーが見つかりません"));

        // 変更：回答対象のPostではなくTopicを取得する
        Topic topic = topicService.findById(topicId)
                .orElseThrow(() ->
                        new IllegalArgumentException("お題が見つかりません"));

        // 変更：CommentではなくAnswerオブジェクトを作成する
        Answer answer = new Answer();
        answer.setTopic(topic);
        answer.setUser(currentUser);
        answer.setContent(content);

        // 変更：AnswerServiceを利用して回答を保存する
        answerService.saveAnswer(answer);

        redirectAttributes.addFlashAttribute(
                "successMessage",
                currentUser.getUsername() + "さんの回答が投稿されました！");

        // 旧URL構造は現段階では維持
        return "redirect:/posts/" + topicId;
    }

    // 変更：CommentではなくAnswerを削除する
    @PostMapping("/{id}/delete")
    public String deleteAnswer(
            @PathVariable Long id,
            @RequestParam Long topicId,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        Answer answer = answerService.getAnswerById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("回答が見つかりません"));

        // PrincipalにはログインIDであるemailが設定されているため、
        // Answerの投稿者emailと比較して本人か判定する

        boolean isLoginUser =
            answer.getUser() != null
                && answer.getUser()
                         .getEmail()
                         .equals(userDetails.getUsername());
        boolean isAdmin = userDetails.getAuthorities()
                .stream()
                .anyMatch(a ->
                        a.getAuthority().equals("ROLE_ADMIN"));

        if (isLoginUser || isAdmin) {
            // AnswerService側で論理削除する
            answerService.deleteAnswer(id);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    answer.getUser().getUsername() + "さんの回答が削除されました！");
        } else {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "回答の削除権限がありません。");
        }

        // 旧URL構造は現段階では維持
        return "redirect:/posts/" + topicId;
    }
}