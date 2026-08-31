package com.example.bulletinboard.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import com.example.bulletinboard.model.AccountStatus;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.ContactRepository;
import com.example.bulletinboard.repository.UserRepository;
import com.example.bulletinboard.security.SecurityConfig;

/**
 * 【クラス全体の役割】
 * AdminControllerのWeb層における管理者機能を検証するテストクラスです。
 *
 * 【主な検証内容】
 * - ROLE_ADMINを持つユーザーが管理画面へアクセスできること
 * - 一般ユーザーが管理画面へアクセスできないこと
 * - 管理者が一般ユーザーのaccountStatusを
 *   ACTIVEからFROZENへ変更できること
 *
 * 管理者による凍結ではaccountNonLockedを使用せず、
 * accountStatusを使用します。
 *
 * また、ログインIDはemailのため、
 * 認証済みユーザーのPrincipalにもemailを使用します。
 */
@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
public class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private ContactRepository contactRepository;

    /**
     * ADMIN権限を持つユーザーが
     * 管理画面へアクセスできることを確認します。
     */
    @Test
    @WithMockUser(
        username = "admin@example.com",
        roles = "ADMIN"
    )
    @DisplayName("【管理者ログイン】ADMIN権限を持つユーザーは管理画面にアクセスできる")
    void adminCanAccessAdminPage() throws Exception {

        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/users"))
                .andExpect(model().attributeExists("users"));
    }

    /**
     * 一般ユーザーが管理画面へアクセスした場合、
     * SecurityConfigの認可によって
     * 403 Forbiddenとなることを確認します。
     */
    @Test
    @WithMockUser(
        username = "user@example.com",
        roles = "USER"
    )
    @DisplayName("【アクセス制限】一般ユーザーが管理画面にアクセスすると403エラーになる")
    void userCannotAccessAdminPage() throws Exception {

        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isForbidden());
    }

    /**
     * 管理者が一般ユーザーを凍結できることを確認します。
     *
     * accountStatusがACTIVEのユーザーに対して
     * 凍結処理を実行するとFROZENへ変更されます。
     *
     * accountNonLockedはログイン失敗による
     * セキュリティロック専用のため、
     * 管理者凍結では使用しません。
     */
    @Test
    @WithMockUser(
        username = "admin@example.com",
        roles = "ADMIN"
    )
    @DisplayName("【違反ユーザー対応】管理者がACTIVEユーザーのアカウントを凍結できる")
    void adminCanFreezeActiveUser() throws Exception {

        // 凍結対象となる一般ユーザー
        User targetUser = new User();
        targetUser.setId(2L);
        targetUser.setUsername("badUser");
        targetUser.setEmail("baduser@example.com");
        targetUser.setRole("ROLE_USER");
        targetUser.setAccountStatus(AccountStatus.ACTIVE);

        // 操作を行う管理者
        User adminUser = new User();
        adminUser.setId(1L);
        adminUser.setUsername("adminUser");
        adminUser.setEmail("admin@example.com");
        adminUser.setRole("ROLE_ADMIN");
        adminUser.setAccountStatus(AccountStatus.ACTIVE);

        when(userRepository.findById(2L))
                .thenReturn(Optional.of(targetUser));

        when(userRepository.findByEmail("admin@example.com"))
                .thenReturn(Optional.of(adminUser));

        mockMvc.perform(
                post("/admin/users/2/toggle-lock")
                    .with(csrf())
        )
        .andExpect(status().is3xxRedirection())
        .andExpect(
                redirectedUrl("/admin/users")
        );

        // ACTIVE → FROZENへ変更されたことを確認
        assertEquals(
                AccountStatus.FROZEN,
                targetUser.getAccountStatus()
        );

        // DB保存処理が呼ばれたことを確認
        verify(userRepository).save(any(User.class));
    }
}