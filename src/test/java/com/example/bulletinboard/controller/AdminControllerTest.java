package com.example.bulletinboard.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.ContactRepository;
import com.example.bulletinboard.repository.UserRepository;
import com.example.bulletinboard.security.SecurityConfig;

/**
 * 【クラスの役割】
 * 管理者機能（AdminController）に対するWeb層（Controller）の単体テストを実施するクラス。
 *
 * 【主な検証内容】
 * 1. ユーザーロール（ADMIN / USER）に応じたアクセス制限・認可制御（403エラーの発生確認）
 * 2. 管理者によるユーザー管理画面の正常表示およびモデルデータの受け渡し
 * 3. 違反ユーザーに対するアカウント凍結（ロック）処理およびDB保存メソッドの呼び出し
 */
@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class) // アプリ全体のセキュリティ設定（権限チェック）をテスト環境に適用
public class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc; // 擬似的なHTTPリクエストを送信するためのクライアント

    @MockitoBean
    private UserRepository userRepository; // モック化されたユーザーリポジトリ

    @MockitoBean
    private ContactRepository contactRepository; // モック化されたお問い合わせリポジトリ

    /**
     * 【テスト内容】ADMINロールを保持するユーザーのアクセス制御検証
     * 【期待結果】HTTPステータス200（OK）が返り、管理者のユーザー一覧画面が表示されること
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("【管理者ログイン】ADMIN権限を持つユーザーは管理画面にアクセスできる")
    void adminCanAccessAdminPage() throws Exception {
        // [実行 & 検証] /admin/users へのGETリクエストを試行
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/users"))
                .andExpect(model().attributeExists("users"));
    }

    /**
     * 【テスト内容】USERロールのみを保持する一般ユーザーのアクセス制御検証
     * 【期待結果】SecurityConfigの認可ルールによりアクセスが拒否され、HTTPステータス403（Forbidden）が返ること
     */
    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("【アクセス制限】一般ユーザーが管理画面にアクセスすると403エラーになる")
    void userCannotAccessAdminPage() throws Exception {
        // [実行 & 検証] 一般ユーザー権限で /admin/users へアクセス
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isForbidden());
    }

    /**
     * 【テスト内容】違反ユーザーに対するアカウント凍結（ロック）実行の検証
     * 【期待結果】ステータスが反転更新され、一覧画面へリダイレクトされるとともに、saveメソッドが呼び出されること
     */
    @Test
    @WithMockUser(username = "adminUser", roles = "ADMIN")
    @DisplayName("【違反ユーザー対応】管理者が別ユーザーのアカウントを凍結できること")
    void adminCanToggleAccountLock() throws Exception {
        // [1. 前提条件の設定 (Given)] 操作対象となる一般ユーザーデータ（ロック前）
        User targetUser = new User();
        targetUser.setId(2L);
        targetUser.setUsername("badUser");
        targetUser.setRole("ROLE_USER");
        targetUser.setAccountNonLocked(true); // 初期状態: 未ロック

        // 操作を実行する管理者ユーザーデータ
        User adminUser = new User();
        adminUser.setId(1L);
        adminUser.setUsername("adminUser");
        adminUser.setRole("ROLE_ADMIN");

        // リポジトリ検索のモック挙動を設定
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetUser));
        when(userRepository.findByUsername("adminUser")).thenReturn(Optional.of(adminUser));

        // [2. 処理実行 (When) & 3. 結果検証 (Then)] CSRFトークンを付与してロック処理（POST）を試行
        mockMvc.perform(post("/admin/users/2/toggle-lock").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));

        // userRepository.save() が確実に実行されたか（振る舞い）を検証
        verify(userRepository).save(any(User.class));
    }
}