package com.example.bulletinboard.exception;

public class AdminUserNotFoundException extends RuntimeException {
    public AdminUserNotFoundException() {
        super("対象ユーザーが見つかりません。");
    }
}
