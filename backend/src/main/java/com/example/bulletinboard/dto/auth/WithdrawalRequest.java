package com.example.bulletinboard.dto.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WithdrawalRequest(
        @NotBlank(message = "現在のパスワードを入力してください。")
        @Size(max = 100, message = "現在のパスワードが正しくありません。")
        String password,
        @AssertTrue(message = "退会内容を確認して明示的に同意してください。")
        boolean confirmed) {
    @Override
    public String toString() {
        return "WithdrawalRequest[password=***, confirmed=" + confirmed + "]";
    }
}
