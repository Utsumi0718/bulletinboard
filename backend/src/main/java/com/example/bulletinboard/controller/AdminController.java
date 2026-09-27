package com.example.bulletinboard.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.bulletinboard.model.AccountStatus;
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
 * - accountStatusを利用したユーザーアカウントの凍結・凍結解除処理
 */

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserRepository userRepository;

    public AdminController(UserRepository userRepository) {
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
 * 【アカウント凍結・凍結解除処理】
 *
 * 指定されたユーザーのaccountStatusを使用して、
 * 管理者によるアカウント凍結・凍結解除を行います。
 *
 * ACTIVE
 *   → FROZENへ変更し、ログインを禁止します。
 *
 * FROZEN
 *   → ACTIVEへ戻し、通常利用可能な状態へ戻します。
 *
 * WITHDRAWN
 *   → 退会済みのため、管理者による凍結・凍結解除の対象外とします。
 *
 * 【設計上のポイント】
 * - accountNonLockedはログイン失敗回数によるセキュリティロック専用です。
 * - 管理者による凍結処理ではaccountNonLockedやfailedAttemptを変更しません。
 * - 管理者自身および他のROLE_ADMINアカウントの状態変更は禁止します。
 */
    @PostMapping("/users/{id}/toggle-lock")
    public String toggleAccountLock(@PathVariable Long id,
                                    @AuthenticationPrincipal UserDetails userDetails,
                                    RedirectAttributes redirectAttributes) {

    //操作対象のユーザーを取得（一致しない場合は一覧画面へ戻る）
    User targetUser = userRepository.findById(id).orElse(null);
    if(targetUser == null){
      redirectAttributes.addFlashAttribute("errorMessage","該当するユーザーは見つかりません");
      return "redirect:/admin/users";
    }

   //現在ログインしている管理者をemailから取得
    User currentUser = userRepository.findByEmail(userDetails.getUsername()).orElse(null);

   //安全チェック
    boolean isSelf = currentUser != null && targetUser.getId().equals(currentUser.getId());
        boolean isTargetAdmin = "ROLE_ADMIN".equals(targetUser.getRole());

        if (isSelf || isTargetAdmin) {
            redirectAttributes.addFlashAttribute("errorMessage", "管理者アカウントの状態を変更することはできません。");
            return "redirect:/admin/users";
        }

        //退会済みユーザーは凍結・解除の対象外
        if(targetUser.getAccountStatus() == AccountStatus.WITHDRAWN){
          redirectAttributes.addFlashAttribute(
              "errorMessage",
             "退会済みユーザーのアカウント状態は変更できません"
          );
        return "redirect:/admin/users";
        }

         // ACTIVE ⇄ FROZEN を切り替える
    if (targetUser.getAccountStatus() == AccountStatus.ACTIVE) {

        targetUser.setAccountStatus(AccountStatus.FROZEN);

        redirectAttributes.addFlashAttribute(
                "successMessage",
                targetUser.getUsername() + " のアカウントを凍結しました"
        );

    } else if (targetUser.getAccountStatus() == AccountStatus.FROZEN) {

        targetUser.setAccountStatus(AccountStatus.ACTIVE);

        redirectAttributes.addFlashAttribute(
                "successMessage",
                targetUser.getUsername() + " のアカウント凍結を解除しました"
        );
    }

    userRepository.save(targetUser);


        return "redirect:/admin/users";
    }

   /**
 * 【ユーザー活動履歴画面】
 *
 * 旧Post / Comment依存を除去するため、
 * 現段階では対象ユーザー情報のみ画面へ渡します。
 *
 * Topic / Answerを利用した活動履歴の再実装は、
 * 後続の機能フェーズで対応します。
 */
@GetMapping("/users/{id}/activities")
public String showUserActivities(
        @PathVariable Long id,
        Model model) {

    User targetUser = userRepository.findById(id)
            .orElseThrow(() ->
                    new IllegalArgumentException("Invalid user Id:" + id));

    model.addAttribute("targetUser", targetUser);

    return "admin/user_activities";
}
}
