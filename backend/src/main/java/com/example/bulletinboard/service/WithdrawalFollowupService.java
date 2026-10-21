package com.example.bulletinboard.service;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.*;
import org.springframework.scheduling.annotation.Scheduled;
import com.example.bulletinboard.model.*;
import com.example.bulletinboard.repository.*;
import com.example.bulletinboard.security.AccountPrincipal;
import com.example.bulletinboard.model.PrivacyRetentionRecord.Target;

/** 個人情報を含めず後続処理を永続化。画像削除は運用承認後のみ明示有効化する。 */
@Service
public class WithdrawalFollowupService {
    private final WithdrawalTaskRepository tasks;
    private final UserRepository users;
    private final ProfileImageRepository images;
    private final ProfileRepository profiles;
    private final PrivacyRetentionService retention;
    private final SessionRegistry sessions;
    private final TransactionTemplate tx;
    private final boolean imageDeletionEnabled;
    public WithdrawalFollowupService(WithdrawalTaskRepository tasks,UserRepository users,ProfileImageRepository images,
            ProfileRepository profiles,PrivacyRetentionService retention,SessionRegistry sessions,
            PlatformTransactionManager manager,@Value("${privacy.profile-image-deletion.enabled:false}") boolean enabled) {
        this.tasks=tasks;this.users=users;this.images=images;this.profiles=profiles;this.retention=retention;
        this.sessions=sessions;this.imageDeletionEnabled=enabled;
        tx=new TransactionTemplate(manager);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void afterWithdrawal(WithdrawalCompletedEvent event) { processSessions(event.userId()); }

    @Scheduled(initialDelayString="${privacy.session-retry-delay-ms:60000}",fixedDelayString="${privacy.session-retry-delay-ms:60000}")
    public void retrySessions() {
        List<WithdrawalTask> pending=tasks.findBySessionStatusNot("DONE",PageRequest.of(0,100));
        for(var task:pending) processSessions(task.getUserId());
    }
    public void processSessions(Long userId) {
        try {
            tx.executeWithoutResult(status -> {
                requireWithdrawn(userId);
                var task=tasks.lock(userId).orElseThrow();
                if("DONE".equals(task.getSessionStatus())) return;
                task.setSessionAttempts(task.getSessionAttempts()+1);
                for(Object principal:sessions.getAllPrincipals()) {
                    if(principal instanceof AccountPrincipal account && account.userId().equals(userId))
                        sessions.getAllSessions(principal,false).forEach(session -> session.expireNow());
                }
                task.setSessionStatus("DONE");task.setLastErrorType(null);task.setUpdatedAt(PrivacyRetentionService.now());tasks.saveAndFlush(task);
            });
        } catch(RuntimeException ex) { failed(userId,false,ex); }
    }
    public void processImages(Long userId) {
        try {
            tx.executeWithoutResult(status -> {
                requireWithdrawn(userId);var task=tasks.lock(userId).orElseThrow();
                if("DONE".equals(task.getImageStatus())) return;
                if(!imageDeletionEnabled) { task.setImageStatus("POLICY_PENDING");tasks.saveAndFlush(task);return; }
                task.setImageAttempts(task.getImageAttempts()+1);
                boolean blocked=false;
                for(var image:images.findByOwnerUserId(userId)) {
                    if(retention.held(Target.PROFILE_IMAGE,image.getId())
                            || profiles.existsByIcon(ProfileImageService.URL_PREFIX+image.getId())) { blocked=true;continue; }
                    images.delete(image);
                }
                images.flush();task.setImageStatus(blocked ? "BLOCKED" : "DONE");
                task.setLastErrorType(null);task.setUpdatedAt(PrivacyRetentionService.now());tasks.saveAndFlush(task);
            });
        } catch(RuntimeException ex) { failed(userId,true,ex); }
    }
    private void requireWithdrawn(Long id) {
        var user=users.findByIdForUpdate(id).orElseThrow();
        if(user.getAccountStatus()!=AccountStatus.WITHDRAWN || user.getPersonalDataErasedAt()==null)
            throw new IllegalStateException("Withdrawal not committed");
    }
    private void failed(Long id,boolean image,RuntimeException failure) {
        // 失敗状態の保存自体が失敗しても、元のPENDINGが残って再試行可能。
        try { tx.executeWithoutResult(status -> tasks.lock(id).ifPresent(task -> {
            if(image) { task.setImageStatus("FAILED");task.setImageAttempts(task.getImageAttempts()+1); }
            else { task.setSessionStatus("FAILED");task.setSessionAttempts(task.getSessionAttempts()+1); }
            task.setLastErrorType(failure.getClass().getSimpleName());task.setUpdatedAt(PrivacyRetentionService.now());tasks.saveAndFlush(task);
        })); } catch(RuntimeException ignored) { /* DB状態による認証拒否は維持する。 */ }
    }
}
