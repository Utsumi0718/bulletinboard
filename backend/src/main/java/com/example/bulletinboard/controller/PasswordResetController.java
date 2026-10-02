package com.example.bulletinboard.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.example.bulletinboard.dto.security.ResetPasswordForm;

/** GでのThymeleaf撤去まで旧画面の表示だけを維持する。更新処理はAPIへ移行済み。 */
@Controller
@RequestMapping("/reset-password")
public class PasswordResetController {
    @GetMapping
    public String showResetPasswordForm(Model model) {
        model.addAttribute("resetPasswordForm", new ResetPasswordForm());
        return "auth/reset-password";
    }
}
