package com.example.bulletinboard.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/*
 * 【クラス全体の役割】
 * パスワード再設定画面の入力値を受け取るDTOクラスです。
 *
 * 新しい認証仕様では、ユーザー名ではなく
 * メールアドレスを基準に対象ユーザーを特定します。
 */
@Data
public class ResetPasswordForm {

    /*
     * パスワード再設定対象のメールアドレス。
     */
    @NotNull(message = "{NotNull.user.email}")
    @Email(message = "{Email.user.email}")
    @Size(max = 255, message = "{Size.user.email}")
    private String email;

    /*
     * 新しいパスワード。
     * 新規登録時と同じバリデーションルールを使用します。
     */
    @NotNull(message = "{NotNull.user.password}")
    @Size(
        min = 8,
        max = 20,
        message = "{Size.user.password}"
    )
    @Pattern(
        regexp = "^(?=.*[a-zA-Z])(?=.*[0-9])[a-zA-Z0-9]+$",
        message = "{Pattern.user.password}"
    )
    private String newPassword;
}