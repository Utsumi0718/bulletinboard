package com.example.bulletinboard.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.bulletinboard.model.*;
import com.example.bulletinboard.repository.*;
import com.example.bulletinboard.security.ActorIds;
import com.example.bulletinboard.exception.ForbiddenOperationException;

@Service
public class PrivacyAccessService {
    private final UserRepository users;
    private final PrivacyAuditRepository audits;
    public PrivacyAccessService(UserRepository users,PrivacyAuditRepository audits) {
        this.users=users; this.audits=audits;
    }
    public User require(String actorKey) {
        User user=users.findById(ActorIds.parse(actorKey)).orElseThrow(() -> new ForbiddenOperationException("この操作は許可されていません。"));
        if(user.getAccountStatus()!=AccountStatus.ACTIVE || !"ROLE_ADMIN".equals(user.getRole()) || !user.isPrivacyReviewer())
            throw new ForbiddenOperationException("この操作は許可されていません。");
        return user;
    }
    /** 記録不能時は閲覧も失敗させる。保持内容を通常ログへ出さない。 */
    @Transactional
    public void audit(String actorKey,String type,String target,String action) {
        User actor=require(actorKey);
        audits.saveAndFlush(new PrivacyAuditEvent(actor.getId(),type,target,action));
    }
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void auditRead(String actorKey,String type,String target,String action) {
        audit(actorKey,type,target,action);
    }
}
