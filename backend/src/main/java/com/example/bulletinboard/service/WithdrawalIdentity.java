package com.example.bulletinboard.service;

import java.util.Locale;
import com.example.bulletinboard.model.AccountStatus;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.dto.common.PublicDisplayName;

public final class WithdrawalIdentity {
    private WithdrawalIdentity() { }
    public static String email(Long id) { return "withdrawn-" + id + "@withdrawn.invalid"; }
    public static String username(Long id) { return "withdrawn_user_" + id; }
    public static boolean reservedEmail(String value) {
        if (value == null) return false;
        String domain = value.substring(value.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT);
        if(domain.endsWith(".")) domain=domain.substring(0,domain.length()-1);
        return domain.equals("invalid") || domain.endsWith(".invalid");
    }
    public static boolean reservedUsername(String value) {
        return value != null && value.toLowerCase(Locale.ROOT).startsWith("withdrawn_user_");
    }
    public static String publicName(User user) { return PublicDisplayName.from(user); }
    public static String publicEmail(User user) {
        return user.getAccountStatus() == AccountStatus.WITHDRAWN || reservedEmail(user.getEmail())
                ? null : user.getEmail();
    }
}
