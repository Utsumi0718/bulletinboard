package com.example.bulletinboard.controller;

import com.example.bulletinboard.dto.ResetPasswordForm;
import com.example.bulletinboard.service.CustomUserDetailsService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;


/*
*パスワードを再設定を制御するController
*/
@Controller
@RequestMapping("/reset-password")

public class PasswordResetController{
 private final CustomUserDetailsService userDetailsService;

 //インジェクション
 public PasswordResetController(CustomUserDetailsService userDetailsService){
    this.userDetailsService = userDetailsService;
 }

 /*
 *パスワードを再設定画面を表示
 */
 @GetMapping
 public String showResetPasswordForm(Model model){
    model.addAttribute("resetPasswordForm", new ResetPasswordForm());
    return "auth/reset-password";
 }

 /**
  * パスワードを再設定処理を実行するメソッド
  */
 @PostMapping
 public String processResetPassword(@Valid @ModelAttribute("resetPasswordForm") ResetPasswordForm form,
                                    BindingResult result,
                                     Model model) {
     //DTOの中身のチェック（8~20文字英数字の組み合わせ）でエラーがある場合
     if(result.hasErrors()){
        return "auth/reset-password";
     }

     //サービスを呼び出してパスワード更新を実行
     boolean isUpdated = userDetailsService.updatePassword(form.getUsername(), form.getNewPassword());

     //該当のユーザーが存在しなかったら
     if(!isUpdated){
      //エラーメッセージを個別に設定して画面へ戻す
      result.rejectValue("username", "error.username","指定されたユーザー名が見つかりません");
      return "auth/reset-password";
     }


     //成功したらログイン画面へリダイレクト（成功クエリを付与）
     return "redirect:/login?register_success";
 }




}