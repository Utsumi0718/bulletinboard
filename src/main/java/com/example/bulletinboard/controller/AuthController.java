package com.example.bulletinboard.controller;

import com.example.bulletinboard.model.User;
import com.example.bulletinboard.service.CustomUserDetailsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;


@Controller
public class AuthController {

   private final CustomUserDetailsService userDetailsService;

   //依存オブジェクトをインジェクション
   public AuthController(CustomUserDetailsService userDetailsService, CustomUserDetailsService customUserDetailsService){
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
       model.addAttribute("user", new User());
       return "auth/register";
   }

  //新規登録処理の実行
  @PostMapping("/register")
  public String register(@ModelAttribute User user){
    userDetailsService.registerUser(user);
    return "redirect:/login?register_success"; //登録後はログイン画面へ
  }




}
