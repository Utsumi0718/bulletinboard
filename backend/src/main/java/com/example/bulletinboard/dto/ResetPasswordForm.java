package com.example.bulletinboard.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
/*
*パスワードの再設定画面の入力値を受け取るDTO
*/
@Data
public class ResetPasswordForm {
   // 対象のユーザー名
    @NotNull(message = "{NotNull.user.username}")
    private String username;

    // 新しいパスワード（登録時と同じバリデーションルール）
    @NotNull(message = "{NotNull.user.password}")
    @Size(min = 8, max = 20, message = "{Size.user.password}")
    @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*[0-9])[a-zA-Z0-9]+$", message = "{Pattern.user.password}")
    private String newPassword;
}
