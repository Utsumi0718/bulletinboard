package com.example.bulletinboard.dto.common;

import com.example.bulletinboard.model.AccountStatus;
import com.example.bulletinboard.model.User;

/** 一般向けDTOの表示名。保存済みの名前や管理者向けの調査情報は変更しない。 */
public final class PublicDisplayName {
    private PublicDisplayName() { }

    public static String from(User user) {
        return user.getAccountStatus() == AccountStatus.WITHDRAWN
                ? "退会済みユーザー" : user.getUsername();
    }
}
