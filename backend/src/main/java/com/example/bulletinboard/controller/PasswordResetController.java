package com.example.bulletinboard.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.example.bulletinboard.dto.ResetPasswordForm;
import com.example.bulletinboard.service.CustomUserDetailsService;

import jakarta.validation.Valid;

/**
 * 【クラス全体の役割】
 * パスワード再設定画面の表示と、
 * メールアドレスを基準としたパスワード再設定処理を担当するControllerクラスです。
 *
 * 【主な役割】
 * - パスワード再設定画面を表示する
 * - ResetPasswordFormの入力値を検証する
 * - emailを基準に対象ユーザーを特定する
 * - CustomUserDetailsServiceを呼び出してパスワードを更新する
 * - 再設定成功後にログイン画面へリダイレクトする
 */
@Controller
@RequestMapping("/reset-password")
public class PasswordResetController {

    private final CustomUserDetailsService userDetailsService;

    /**
     * CustomUserDetailsServiceをコンストラクタインジェクションします。
     */
    public PasswordResetController(
            CustomUserDetailsService userDetailsService) {

        this.userDetailsService = userDetailsService;
    }

    /**
     * パスワード再設定画面を表示します。
     */
    @GetMapping
    public String showResetPasswordForm(Model model) {

        model.addAttribute(
                "resetPasswordForm",
                new ResetPasswordForm()
        );

        return "auth/reset-password";
    }

    /**
     * パスワード再設定処理を実行します。
     *
     * emailを基準に対象ユーザーを検索し、
     * 新しいパスワードへ更新します。
     */
    @PostMapping
    public String processResetPassword(
            @Valid
            @ModelAttribute("resetPasswordForm")
            ResetPasswordForm form,
            BindingResult result) {

        /*
         * メールアドレスや新しいパスワードの
         * バリデーションエラーがある場合は、
         * 再設定画面へ戻します。
         */
        if (result.hasErrors()) {
            return "auth/reset-password";
        }

        /*
         * emailを基準に対象ユーザーを特定し、
         * パスワード更新処理を実行します。
         */
        boolean isUpdated =
                userDetailsService.updatePassword(
                        form.getEmail(),
                        form.getNewPassword()
                );

        /*
         * 指定されたemailに該当するユーザーが
         * 存在しない場合は、emailフィールドへ
         * エラーメッセージを設定します。
         */
        if (!isUpdated) {

            result.rejectValue(
                    "email",
                    "error.email",
                    "指定されたメールアドレスが見つかりません"
            );

            return "auth/reset-password";
        }

        /*
         * パスワード再設定成功後は、
         * 成功パラメータを付与してログイン画面へ遷移します。
         */
        return "redirect:/login?reset_success";
    }
}