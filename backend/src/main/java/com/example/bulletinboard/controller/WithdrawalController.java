package com.example.bulletinboard.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import com.example.bulletinboard.service.CustomUserDetailsService;

/**
 * 【クラス全体の役割】
 * ログイン中ユーザーの退会処理に関する
 * HTTPリクエストを制御するControllerクラスです。
 *
 * 【主な役割】
 * - 退会確認画面を表示する
 * - 認証中ユーザーのemailを取得する
 * - Service層へ退会処理を依頼する
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
 * 退会完了後のログアウト処理は、
 * 次の実装段階で追加します。
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
     * 退会確認画面を表示します。
     */
    @GetMapping("/account/withdraw")
    public String showWithdrawalPage() {

        return "auth/withdraw";
    }

    /**
     * ログイン中ユーザーの退会処理を実行します。
     *
     * Spring SecurityのPrincipalからemailを取得し、
     * Service層へ退会処理を依頼します。
     */
    @PostMapping("/account/withdraw")
    public String withdraw(
            @AuthenticationPrincipal UserDetails userDetails) {

        String email = userDetails.getUsername();

        boolean withdrawn =
                userDetailsService.withdrawUser(email);

        if (!withdrawn) {
            return "redirect:/account/withdraw?error";
        }

        return "redirect:/login?withdraw_success";
    }
}