package com.example.bulletinboard.model;

/** 受付時の理由。識別子は既存DB定義と承認済み仕様に合わせる。 */
public enum ReportReason {
    ABUSE("誹謗中傷・嫌がらせ"),
    RIGHTS_VIOLATION("著作権などの権利侵害"),
    INAPPROPRIATE("不適切な内容"),
    PRIVACY("プライバシー侵害"),
    SOLICITATION("勧誘・宣伝"),
    SPAM("スパム・迷惑行為"),
    OTHER("その他");

    private final String displayName;

    ReportReason(String displayName) { this.displayName = displayName; }

    public String getDisplayName() { return displayName; }
}
