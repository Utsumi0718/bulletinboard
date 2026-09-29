package com.example.bulletinboard.exception;

public class AdminProfileNotFoundException extends RuntimeException {
    public AdminProfileNotFoundException() { super("対象プロフィールが見つかりません。"); }
}
