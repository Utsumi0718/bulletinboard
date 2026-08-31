package com.example.bulletinboard.dto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;


/**
 * 【クラス全体の役割】
 * 新規ユーザー登録画面のフォーム入力値を受け取り、
 * 画面用のバリデーション（入力チェック）を行うDTOクラスです。
 *
 * usernameはサービス上で公開するユーザー名、
 * emailはログインID、
 * passwordは認証用パスワードとして使用します。
 *
 * Userエンティティと入力フォームを分離することで、
 * ハッシュ化前のパスワードなどを安全に扱います。
 */
@Data
public class RegisterForm {

    // --------------------------------------------------
    // ユーザー名（username）
    // --------------------------------------------------
    @NotNull(message = "{NotNull.user.username}")
    @Size(min = 4, max = 10, message = "{Size.user.username}")
    @Pattern(
            regexp = "^(?=.*[a-zA-Z])(?=.*[0-9])[a-zA-Z0-9]+$",
            message = "{Pattern.user.username}"
    )
    private String username;

    // --------------------------------------------------
    // メールアドレス（email）
    // --------------------------------------------------
    @NotNull(message = "{NotNull.user.email}")
    @Email(message = "{Email.user.email}")
    @Size(max = 255, message = "{Size.user.email}")
    private String email;

    // --------------------------------------------------
    // パスワード（password）
    // --------------------------------------------------
    @NotNull(message = "{NotNull.user.password}")
    @Size(min = 8, max = 20, message = "{Size.user.password}")
    @Pattern(
            regexp = "^(?=.*[a-zA-Z])(?=.*[0-9])[a-zA-Z0-9]+$",
            message = "{Pattern.user.password}"
    )
    private String password;
}