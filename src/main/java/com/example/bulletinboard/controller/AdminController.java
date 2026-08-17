package com.example.bulletinboard.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.UserRepository;



/**
 * 【クラスの役割】
 * システム管理専用の画面表示および操作処理を担当するControllerクラス。
 * '/admin'以下のパスで管理される各種管理リクエストを受け付け切なサービス・リポジトリ処理を呼び出して結果を画面（View）へ渡す。
 *
 * 【主な役割】
 * - 管理者ダッシュボードおよびユーザー一覧画面のルーティング（表示処理）
 * - DBからの登録ユーザー一覧データ取得とThymeleafビューへの受け渡し
 * - ユーザーアカウントの凍結（ロック）および凍結解除のステータス更新処理
 */

@Controller
@RequestMapping("/admin")
public class AdminController {

    private UserRepository userRepository;

    //インジェクション
    public AdminController(UserRepository userRepository){
      this.userRepository = userRepository;
    }


    /*
     * 【ダッシュボード初期表示・リダイレクト処理】
     * `/admin` や `/admin/dashboard` へのアクセスを、メイン機能であるユーザー管理画面（/admin/users）へ転送。
     */
    @GetMapping({"","/", "/dashboard"})
    public String dashboard() {
        return "redirect:/admin/users";
    }


   /*
     * 【ユーザー一覧画面表示処理】
     * DBに登録されている全ユーザー情報を取得し、モデルに格納して `admin/users.html` をレンダリング。
     */
    @GetMapping("/users")
    public String listUsers(Model model) {
      List<User> users = userRepository.findAll();
      model.addAttribute("users",users);
        return "admin/users";
    }


    /*
     * 【アカウント凍結・解除切り替え処理】
     * 指定された ID のユーザーのアカウントロック状態（accountNonLocked）を反転。
     * ロック解除時にはログイン失敗回数（failedAttempt）もリセット。
     */
    @PostMapping("/users/{id}/toggle-lock")
    public String toggleAccountLock(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        userRepository.findById(id).ifPresent(user -> {
          boolean currentStatus = user.isAccountNonLocked();//現在のアカウントの状態を取得
          user.setAccountNonLocked(!currentStatus);//アカウントの状態を反転

          //凍結解除時は失敗回数をリセット
          if(!currentStatus){
            user.setFailedAttempt(0);
          }
          userRepository.save(user);

         //フラッシュメッセージ
         String statusMessage = !currentStatus ? "アカウントのロックを解除しました" : "アカウントを凍結しました";
         redirectAttributes.addFlashAttribute("successMessage", user.getUsername() + " の" + statusMessage);

        });
        return "redirect:/admin/users";
    }



}
