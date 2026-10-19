package com.example.bulletinboard.exception;

/** レート制限。入力内容やIPをメッセージへ含めない。 */
public class ContactRateLimitException extends RuntimeException {
    public ContactRateLimitException() { super("短時間に多くのお問い合わせが送信されています。時間をおいてからお試しください。"); }
}
