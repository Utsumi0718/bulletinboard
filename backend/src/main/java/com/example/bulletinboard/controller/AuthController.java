package com.example.bulletinboard.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import com.example.bulletinboard.dto.user.RegisterForm;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.service.CustomUserDetailsService;

import jakarta.validation.Valid;

/**
 * 【クラス全体の役割】
 * ユーザー認証（ログイン画面の表示）および新規ユーザー登録に関するリクエストを制御するコントローラークラスです。
 * 画面からの入力を受け取り、バリデーションチェック（入力検証）を行った上で、
 * サービス層（CustomUserDetailsService）を呼び出してユーザー情報の登録処理や画面遷移を制御します。
 */
@Controller
public class AuthController {

   private final CustomUserDetailsService userDetailsService;

   //依存オブジェクトをインジェクション
   public AuthController(CustomUserDetailsService userDetailsService){
     this.userDetailsService = userDetailsService;
     }

   //ログイン画面の表示
   @GetMapping("/login")
   public String login(){
     return "auth/login";
   }

   //新規登録画面の表示
   @GetMapping("/register")
   public String registerForm(Model model) {
       model.addAttribute("registerForm", new RegisterForm()); //Form
       return "auth/register";
   }

  //新規登録処理の実行
  @PostMapping("/register")
  public String register(@Valid @ModelAttribute("registerForm") RegisterForm form, BindingResult result){

    //公開ユーザー名の重複チェックの
    if(userDetailsService.existsByUsername(form.getUsername())){
     //"username"フィールドに重複エラーメッセージを割り当てる
     result.rejectValue("username","duplicate","すでに登録されてるユーザー名です");
    }

    //追記：ログイン用のメールアドレスの重複チェック
    if(userDetailsService.existsByEmail(form.getEmail())){
     result.rejectValue("email","duplicate","すでに登録されてるメールアドレスです");
     }

    //バリテーションエラーがある場合は登録画面に戻る
    if(result.hasErrors()){
      return "auth/register";
    }

    // Form から User エンティティへ値を移し替える
        User user = new User();
        user.setUsername(form.getUsername());
        user.setEmail(form.getEmail());
        user.setPassword(form.getPassword());

    // パスワードのハッシュ化とユーザー登録はService側で行う
    userDetailsService.registerUser(user);
    return "redirect:/login?register_success"; //登録後はログイン画面へ
  }




}
