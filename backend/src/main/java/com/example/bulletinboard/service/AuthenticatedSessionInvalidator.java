package com.example.bulletinboard.service;

import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** パスワード変更のコミット後、同じUser IDのSessionを期限切れにする。 */
@Component
public class AuthenticatedSessionInvalidator {
    private final SessionRegistry sessions;

    public AuthenticatedSessionInvalidator(SessionRegistry sessions) {
        this.sessions = sessions;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void invalidate(PasswordResetCompletedEvent event) {
        sessions.getAllPrincipals().stream()
                .filter(com.example.bulletinboard.security.AccountPrincipal.class::isInstance)
                .map(com.example.bulletinboard.security.AccountPrincipal.class::cast)
                .filter(principal -> principal.userId().equals(event.userId()))
                .forEach(principal -> sessions.getAllSessions(principal, false)
                        .forEach(session -> session.expireNow()));
    }
}
