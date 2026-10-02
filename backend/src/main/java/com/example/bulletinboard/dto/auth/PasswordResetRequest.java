package com.example.bulletinboard.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
        @NotBlank(message = "メールアドレスを入力してください。")
        @Email(message = "メールアドレスの形式が正しくありません。")
        @Size(max = 255, message = "メールアドレスは255文字以内で入力してください。")
        String email) {
    @Override
    public String toString() {
        return "PasswordResetRequest[email=***]";
    }
}
