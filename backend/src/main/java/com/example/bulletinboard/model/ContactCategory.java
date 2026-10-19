package com.example.bulletinboard.model;

/** 新規受付の正式コード。既存レコードの未設定値はnullのまま保持する。 */
public enum ContactCategory {
    USAGE("サービスの使い方について"),
    BUG_REPORT("不具合・エラーの報告"),
    ACCOUNT("アカウント・ログインについて"),
    WITHDRAWAL("退会手続きについて"),
    RIGHTS_PRIVACY("著作権・肖像権・プライバシーについて"),
    OTHER("その他");

    private final String label;
    ContactCategory(String label) { this.label = label; }
    public String getLabel() { return label; }
}
