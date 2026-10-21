package com.example.bulletinboard.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.bulletinboard.exception.PasswordResetException;
import com.example.bulletinboard.model.AccountStatus;
import com.example.bulletinboard.model.PasswordResetToken;
import com.example.bulletinboard.repository.PasswordResetTokenRepository;
import com.example.bulletinboard.repository.UserRepository;

/** パスワード再設定トークンとパスワード更新をDBトランザクション内で扱う。 */
@Service
public class PasswordResetService {
    static final int VALID_MINUTES = 30;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder passwords;
    private final ApplicationEventPublisher events;
    private final EntityManager entityManager;

    public PasswordResetService(UserRepository users, PasswordResetTokenRepository tokens,
            PasswordEncoder passwords, ApplicationEventPublisher events,EntityManager entityManager) {
        this.users = users;
        this.tokens = tokens;
        this.passwords = passwords;
        this.events = events;
        this.entityManager=entityManager;
    }

    /** 未登録・退会済みの場合も呼び出し元は同じ公開応答を返す。 */
    @Transactional
    public Optional<IssuedToken> issue(String email) {
        return users.findByEmailForUpdate(email)
                .filter(user -> user.getAccountStatus() != AccountStatus.WITHDRAWN)
                .map(user -> {
                    String raw = newRawToken();
                    PasswordResetToken token = tokens.findByUserId(user.getId())
                            .orElseGet(PasswordResetToken::new);
                    token.setUser(user);
                    token.setTokenHash(hash(raw));
                    token.setExpiresAt(LocalDateTime.now().plusMinutes(VALID_MINUTES));
                    tokens.saveAndFlush(token);
                    return new IssuedToken(email, raw);
                });
    }

    @Transactional
    public void revoke(String rawToken) {
        tokens.findByTokenHash(hash(rawToken)).ifPresent(tokens::delete);
    }

    @Transactional
    public void confirm(String rawToken, String newPassword) {
        Long owner=tokens.findOwnerId(hash(rawToken))
                .orElseThrow(() -> new PasswordResetException(PasswordResetException.Reason.INVALID));
        var user=users.findByIdForUpdate(owner)
                .orElseThrow(() -> new PasswordResetException(PasswordResetException.Reason.INVALID));
        entityManager.refresh(user,LockModeType.PESSIMISTIC_WRITE);
        PasswordResetToken token = tokens.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new PasswordResetException(PasswordResetException.Reason.INVALID));
        if (!token.getExpiresAt().isAfter(LocalDateTime.now())
                || user.getAccountStatus() == AccountStatus.WITHDRAWN || !token.getUser().getId().equals(owner)) {
            throw new PasswordResetException(PasswordResetException.Reason.INVALID);
        }

        user.setPassword(passwords.encode(newPassword));
        user.setFailedAttempt(0);
        user.setAccountNonLocked(true);
        user.setAuthenticationVersion(user.getAuthenticationVersion()+1);
        users.saveAndFlush(user);
        tokens.delete(token);
        events.publishEvent(new PasswordResetCompletedEvent(user.getId()));
    }

    public static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static String newRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record IssuedToken(String email, String rawToken) {
        @Override public String toString() { return "IssuedToken[credentials=REDACTED]"; }
    }
}
