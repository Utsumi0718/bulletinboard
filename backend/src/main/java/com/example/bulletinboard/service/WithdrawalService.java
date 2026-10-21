package com.example.bulletinboard.service;

import java.time.LocalDateTime;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.model.WithdrawalTask;
import com.example.bulletinboard.model.PrivacyRetentionRecord.Target;
import com.example.bulletinboard.repository.*;
import com.example.bulletinboard.security.ActorIds;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.bulletinboard.exception.WithdrawalException;
import com.example.bulletinboard.model.AccountStatus;
import com.example.bulletinboard.repository.UserRepository;

/** Userの参照IDを保持し、認証情報・Profile・トークン・後続処理を同一DB取引で更新する。 */
@Service
@Import(PrivacyModule.class)
public class WithdrawalService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final ProfileRepository profiles;
    private final PasswordResetTokenRepository tokens;
    private final ProfileImageRepository images;
    private final PrivacyRetentionService retention;
    private final WithdrawalTaskRepository tasks;
    private final ApplicationEventPublisher events;
    private final EntityManager entityManager;
    private static final SecureRandom RANDOM=new SecureRandom();

    public WithdrawalService(UserRepository users, PasswordEncoder passwords,ProfileRepository profiles,
            PasswordResetTokenRepository tokens,ProfileImageRepository images,PrivacyRetentionService retention,
            WithdrawalTaskRepository tasks,ApplicationEventPublisher events,EntityManager entityManager) {
        this.users = users;
        this.passwords = passwords;
        this.profiles=profiles;this.tokens=tokens;this.images=images;this.retention=retention;
        this.tasks=tasks;this.events=events;this.entityManager=entityManager;
    }

    @Transactional
    public void withdraw(String actorId, String rawPassword) {
        Long id=ActorIds.parse(actorId);
        // Profile→User→Token。画像変更と逆順のロックを作らない。
        var profile=profiles.findByUserIdForUpdate(id);
        var user = users.findByIdForUpdate(id)
                .orElseThrow(() -> new WithdrawalException(WithdrawalException.Reason.FORBIDDEN));
        entityManager.refresh(user,LockModeType.PESSIMISTIC_WRITE);
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new WithdrawalException(WithdrawalException.Reason.FORBIDDEN);
        }
        if (!passwords.matches(rawPassword, user.getPassword())) {
            throw new WithdrawalException(WithdrawalException.Reason.PASSWORD_MISMATCH);
        }

        erase(user);
        profile.ifPresent(p -> { p.setBio(null);p.setIcon(null);profiles.saveAndFlush(p); });
    }

    /** 実データへの呼び出しは別承認。既存退会者だけをID単位で再実行可能に処理する。 */
    @Transactional
    public void eraseExisting(Long id) {
        var profile=profiles.findByUserIdForUpdate(id);
        User user=users.findByIdForUpdate(id).orElseThrow();
        entityManager.refresh(user,LockModeType.PESSIMISTIC_WRITE);
        if(user.getAccountStatus()!=AccountStatus.WITHDRAWN) throw new IllegalArgumentException("退会済みではありません。");
        if(user.getPersonalDataErasedAt()!=null) return;
        if(WithdrawalIdentity.reservedEmail(user.getEmail()) || WithdrawalIdentity.reservedUsername(user.getUsername()))
            throw new IllegalStateException("Previously transformed identity requires review");
        erase(user);profile.ifPresent(p -> {p.setBio(null);p.setIcon(null);profiles.saveAndFlush(p);});
    }

    private void erase(User user) {
        String email=WithdrawalIdentity.email(user.getId()), name=WithdrawalIdentity.username(user.getId());
        if(users.findByEmail(email).filter(other -> !other.getId().equals(user.getId())).isPresent()
                || users.findByUsername(name).filter(other -> !other.getId().equals(user.getId())).isPresent())
            throw new IllegalStateException("Reserved identity collision");
        retention.preserveBeforeErasure(user);
        tokens.findByUserId(user.getId()).ifPresent(tokens::delete);tokens.flush();
        byte[] secret=new byte[32];RANDOM.nextBytes(secret);
        user.setPassword(passwords.encode(Base64.getUrlEncoder().withoutPadding().encodeToString(secret)));
        java.util.Arrays.fill(secret,(byte)0);
        user.setEmail(email);user.setUsername(name);user.setFailedAttempt(0);user.setAccountNonLocked(true);
        user.setAccountStatus(AccountStatus.WITHDRAWN);
        if(user.getWithdrawnAt()==null) user.setWithdrawnAt(LocalDateTime.now());
        user.setPersonalDataErasedAt(PrivacyRetentionService.now());
        user.setAuthenticationVersion(user.getAuthenticationVersion()+1);
        users.saveAndFlush(user);
        for(String imageId:images.findIdsByOwner(user.getId())) retention.pending(Target.PROFILE_IMAGE,imageId,user.getId(),null);
        WithdrawalTask task=new WithdrawalTask();task.setUserId(user.getId());tasks.saveAndFlush(task);
        events.publishEvent(new WithdrawalCompletedEvent(user.getId()));
    }
}
