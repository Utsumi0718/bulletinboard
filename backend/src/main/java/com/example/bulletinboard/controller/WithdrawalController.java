package com.example.bulletinboard.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;

import com.example.bulletinboard.service.CustomUserDetailsService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 【クラス全体の役割】
 * ログイン中ユーザーの退会処理に関する
 * HTTPリクエストを制御するControllerクラスです。
 *
 * 【主な役割】
 * - 認証中ユーザーのemailを取得する
 * - Service層へ退会処理を依頼する
 * - 退会成功後に認証情報とHTTPセッションを破棄する
 * - 退会処理の結果に応じて画面遷移を制御する
 *
 * 【設計上のポイント】
 * このControllerではUserエンティティを直接更新しません。
 *
 * accountStatusをWITHDRAWNへ変更する処理や
 * withdrawnAtを設定する処理はService層で行います。
 *
 * また、ログインIDはusernameではなくemailのため、
 * UserDetails#getUsername()から取得できる値はemailとして扱います。
 *
 * 退会確認画面はReact移行時に実装するため、
 * 現段階ではPOSTによる退会実行処理のみを担当します。
 */

@Controller
public class WithdrawalController {

    private final CustomUserDetailsService userDetailsService;

    /**
     * 必要なServiceをコンストラクタインジェクションします。
     */
    public WithdrawalController(
            CustomUserDetailsService userDetailsService) {

        this.userDetailsService = userDetailsService;
    }



    /**
     * ログイン中ユーザーの退会処理を実行します。
     *
     * Spring SecurityのPrincipalからemailを取得し、
     * Service層へ退会処理を依頼します。
     *
     * 退会処理成功後は、
     * Spring Securityの認証情報とHTTPセッションを破棄します。
     */
    @PostMapping("/account/withdraw")
    public String withdraw(
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest request,
            HttpServletResponse response) {

        String email = userDetails.getUsername();

        boolean withdrawn =
                userDetailsService.withdrawUser(email);

        if (!withdrawn) {
            return "redirect:/account/withdraw?error";
        }


               new SecurityContextLogoutHandler()
                .logout(
                    request,
                    response,
                    org.springframework.security.core.context
                        .SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                );

        return "redirect:/login?withdraw_success";
    }
}