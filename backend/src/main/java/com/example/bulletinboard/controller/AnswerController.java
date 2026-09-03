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
 * ログインユーザーのemailやROLE_ADMIN権限など、
 * Serviceで業務ルールを判定するために必要な情報を取得して渡します。
 *
 * 回答の削除可否などの業務ルール判定は、
 * AnswerService側へ委譲します。
 *
 * このクラスは旧CommentControllerをAnswer仕様へ移行する途中段階のControllerです。
 * 現在は旧Thymeleaf画面と旧URL構造を維持しています。
 * Answerの入力バリデーション・編集処理・REST API化については、
 * feature/topic-answer-api内の後続工程で整理します。
 */


@Controller
@RequestMapping("/comments")
public class AnswerController {

    private final AnswerService answerService;
    private final TopicService topicService;
    private final CustomUserDetailsService userDetailsService;

    /*
     * AnswerService、TopicService、CustomUserDetailsServiceを
     * コンストラクタインジェクションします。
    */
    public AnswerController(
            AnswerService answerService,
            TopicService topicService,
            CustomUserDetailsService userDetailsService) {

        this.answerService = answerService;
        this.topicService = topicService;
        this.userDetailsService = userDetailsService;
    }

    /*
     * Topicに対して新しいAnswerを投稿します。
     */
    @PostMapping("/add")
    public String addAnswer(
            @RequestParam Long topicId,
            @RequestParam String content,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes) {

         /*
          * PrincipalにはログインIDであるemailが設定されているため、
          * emailを基準にログインユーザーを取得します。
          */

         User currentUser = userDetailsService
            .findByEmail(userDetails.getUsername())
            .orElseThrow(() ->
                new IllegalArgumentException("ユーザーが見つかりません"));


        Topic topic = topicService.findById(topicId)
                .orElseThrow(() ->
                        new IllegalArgumentException("お題が見つかりません"));

        /*
         * 回答対象のTopicとログインユーザーを紐付けて
         * Answerオブジェクトを生成します。
        */

        Answer answer = new Answer();
        answer.setTopic(topic);
        answer.setUser(currentUser);
        answer.setContent(content);

        answerService.saveAnswer(answer);

        redirectAttributes.addFlashAttribute(
                "successMessage",
                currentUser.getUsername() + "さんの回答が投稿されました！");


        return "redirect:/posts/" + topicId;
    }

    /*
    * 指定されたAnswerを論理削除します。
    *
    * Controllerではログインユーザーのemailと
    * ROLE_ADMIN権限の有無を取得し、
    * 削除可否の業務ルール判定はAnswerServiceへ委譲します。
    */
     @PostMapping("/{id}/delete")
     public String deleteAnswer(
        @PathVariable Long id,
        @RequestParam Long topicId,
        @AuthenticationPrincipal UserDetails userDetails,
        RedirectAttributes redirectAttributes) {

    Answer answer = answerService.getAnswerById(id)
            .orElseThrow(() ->
                    new IllegalArgumentException("回答が見つかりません"));

    boolean isAdmin = userDetails.getAuthorities()
            .stream()
            .anyMatch(a ->
                    a.getAuthority().equals("ROLE_ADMIN"));

    answerService.deleteAnswer(
            id,
            userDetails.getUsername(),
            isAdmin
    );

    redirectAttributes.addFlashAttribute(
            "successMessage",
            answer.getUser().getUsername() + "さんの回答が削除されました！");

      return "redirect:/posts/" + topicId;
  }
}