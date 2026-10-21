package com.example.bulletinboard.security;

import org.springframework.security.authentication.BadCredentialsException;

/** 認証後の内部引数。メールアドレスへのフォールバックは許可しない。 */
public final class ActorIds {
    private ActorIds() { }
    public static Long parse(String key) {
        try {
            if (key == null || !key.matches("[1-9][0-9]{0,18}")) throw new NumberFormatException();
            return Long.valueOf(key);
        } catch (NumberFormatException ex) {
            throw new BadCredentialsException("ログインが必要です。");
        }
    }
}
