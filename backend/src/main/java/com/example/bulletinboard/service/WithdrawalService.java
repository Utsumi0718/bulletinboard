package com.example.bulletinboard.service;

import java.time.LocalDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.bulletinboard.exception.WithdrawalException;
import com.example.bulletinboard.model.AccountStatus;
import com.example.bulletinboard.repository.UserRepository;

/** 現在のパスワードを確認し、Userだけを論理退会状態へ更新する。 */
@Service
public class WithdrawalService {
    private final UserRepository users;
    private final PasswordEncoder passwords;

    public WithdrawalService(UserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    @Transactional
    public void withdraw(String email, String rawPassword) {
        var user = users.findByEmailForUpdate(email)
                .orElseThrow(() -> new WithdrawalException(WithdrawalException.Reason.FORBIDDEN));
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new WithdrawalException(WithdrawalException.Reason.FORBIDDEN);
        }
        if (!passwords.matches(rawPassword, user.getPassword())) {
            throw new WithdrawalException(WithdrawalException.Reason.PASSWORD_MISMATCH);
        }

        user.setAccountStatus(AccountStatus.WITHDRAWN);
        user.setWithdrawnAt(LocalDateTime.now());
        users.saveAndFlush(user);
    }
}
