package com.example.bulletinboard.security;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

/** メール・表示名を保存せず、不変のUser IDでSessionの本人を識別する。 */
public final class AccountPrincipal extends User {
    private static final long serialVersionUID = 1L;
    private final Long userId;
    private final long authenticationVersion;

    public AccountPrincipal(Long userId, String password, boolean enabled, boolean nonLocked,
            Collection<? extends GrantedAuthority> authorities) {
        this(userId,password,enabled,nonLocked,authorities,0);
    }
    public AccountPrincipal(Long userId, String password, boolean enabled, boolean nonLocked,
            Collection<? extends GrantedAuthority> authorities,long authenticationVersion) {
        super(userId.toString(), password, enabled, true, true, nonLocked, authorities);
        if (userId <= 0) throw new IllegalArgumentException("Invalid account ID");
        this.userId = userId;
        this.authenticationVersion=authenticationVersion;
    }

    public Long userId() { return userId; }
    public long authenticationVersion() { return authenticationVersion; }

    @Override public String toString() { return "AccountPrincipal[id=" + userId + "]"; }
}
