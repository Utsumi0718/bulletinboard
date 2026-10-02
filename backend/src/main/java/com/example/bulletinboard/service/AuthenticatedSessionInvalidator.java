package com.example.bulletinboard.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** パスワード変更がコミットされた後、同じメールアドレスの全Sessionを期限切れにする。 */
@Component
public class AuthenticatedSessionInvalidator {
    private final SessionRegistry sessions;

    public AuthenticatedSessionInvalidator(SessionRegistry sessions) {
        this.sessions = sessions;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void invalidate(PasswordResetCompletedEvent event) {
        sessions.getAllPrincipals().stream()
                .filter(UserDetails.class::isInstance)
                .map(UserDetails.class::cast)
                .filter(principal -> principal.getUsername().equals(event.email()))
                .forEach(principal -> sessions.getAllSessions(principal, false)
                        .forEach(session -> session.expireNow()));
    }
}
