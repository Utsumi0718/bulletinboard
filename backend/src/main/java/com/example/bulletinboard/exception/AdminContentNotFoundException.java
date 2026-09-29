package com.example.bulletinboard.exception;

public class AdminContentNotFoundException extends RuntimeException {
    public AdminContentNotFoundException() { super("対象コンテンツが見つかりません。"); }
}
