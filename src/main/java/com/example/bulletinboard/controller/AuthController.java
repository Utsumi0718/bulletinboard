package com.example.bulletinboard.controller;

import com.example.bulletinboard.dto.RegisterForm;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.service.CustomUserDetailsService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;


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
   public String resisterForm(Model model) {
       model.addAttribute("registerForm", new RegisterForm()); //Form
       return "auth/register";
   }

  //新規登録処理の実行
  @PostMapping("/register")
  public String register(@Valid @ModelAttribute("registerForm") RegisterForm form, BindingResult result){
    //バリテーションエラーがある場合は登録画面に戻る
    if(result.hasErrors()){
      return "auth/register";
    }

    // Form から User エンティティへ値を移し替える
        User user = new User();
        user.setUsername(form.getUsername());
        user.setPassword(form.getPassword());
    userDetailsService.registerUser(user);
    return "redirect:/login?register_success"; //登録後はログイン画面へ
  }




}
