package com.example.bulletinboard.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 登録用の認証情報。文字列表現には入力値を含めない。 */
public record RegisterRequest(
        @NotBlank(message = "ユーザー名を入力してください")
        @Size(min = 4, max = 10, message = "ユーザー名は4文字以上10文字以内で入力してください")
        @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*[0-9])[a-zA-Z0-9]+$",
                message = "ユーザー名は半角英字と半角数字を両方含めて入力してください")
        String username,

        @NotBlank(message = "メールアドレスを入力してください")
        @Email(message = "正しいメールアドレスの形式で入力してください")
        @Size(max = 255, message = "メールアドレスは255文字以内で入力してください")
        String email,

        @NotBlank(message = "パスワードを入力してください")
        @Size(min = 8, max = 20, message = "パスワードは8文字以上20文字以内で入力してください")
        @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*[0-9])[a-zA-Z0-9]+$",
                message = "パスワードは半角英字と半角数字を両方含めて入力してください")
        String password) {

    @Override
    public String toString() {
        return "RegisterRequest[credentials=REDACTED]";
    }
}
