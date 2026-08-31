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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.bulletinboard.model.AccountStatus;
import com.example.bulletinboard.model.Contact;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.ContactRepository;
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
    private final ContactRepository contactRepository; // 追記

    // コンストラクタに ContactRepository を追加
    public AdminController(UserRepository userRepository, ContactRepository contactRepository) {
        this.userRepository = userRepository;
        this.contactRepository = contactRepository;
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
     * 【追記】安全装置：管理者自身および他管理者アカウントの凍結・解除を禁止。
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
   /*
     * 【追記：お問い合わせ一覧画面表示処理】
     */
    @GetMapping("/contacts")
    public String listContacts(Model model) {
        List<Contact> contacts = contactRepository.findAll();
        model.addAttribute("contacts", contacts);
        return "admin/contacts";
    }

    /*
     * 【追記：お問い合わせステータス更新処理】
     */
    @PostMapping("/contacts/{id}/status")
    public String updateContactStatus(@PathVariable Long id,
                                      @RequestParam String status,
                                      RedirectAttributes redirectAttributes) {
        Contact contact = contactRepository.findById(id).orElse(null);
        if (contact != null) {
            contact.setStatus(status);
            contactRepository.save(contact);
            redirectAttributes.addFlashAttribute("successMessage", "ステータスを更新しました。");
        }
        return "redirect:/admin/contacts";
    }

    /*
     * 【追記：お問い合わせ削除処理】
     */
    @PostMapping("/contacts/{id}/delete")
    public String deleteContact(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        if (contactRepository.existsById(id)) {
            contactRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("successMessage", "お問い合わせを削除しました。");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "指定されたお問い合わせが見つかりませんでした。");
        }
        return "redirect:/admin/contacts";
    }
   }
