package com.example.bulletinboard.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
        @NotBlank(message = "再設定トークンを入力してください。")
        @Size(max = 255, message = "再設定トークンが正しくありません。")
        String token,
        @NotBlank(message = "新しいパスワードを入力してください。")
        @Size(min = 8, max = 20, message = "パスワードは8文字以上20文字以内で入力してください。")
        @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*[0-9])[a-zA-Z0-9]+$",
                message = "パスワードは半角英字と数字を組み合わせてください。")
        String newPassword) {
    @Override
    public String toString() {
        return "PasswordResetConfirmRequest[token=***, newPassword=***]";
    }
}
