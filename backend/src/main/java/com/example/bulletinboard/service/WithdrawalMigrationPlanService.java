package com.example.bulletinboard.service;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.bulletinboard.model.AccountStatus;
import com.example.bulletinboard.repository.UserRepository;

/** 移行の対象を明示指定して確認するだけ。自動バックフィル・一括更新は実装しない。 */
@Service
public class WithdrawalMigrationPlanService {
    public record Preview(Long userId,String status) { }
    private final UserRepository users;
    private final PrivacyAccessService access;
    public WithdrawalMigrationPlanService(UserRepository users,PrivacyAccessService access) { this.users=users;this.access=access; }
    @Transactional(readOnly=true)
    public List<Preview> preview(List<Long> ids,String actorId) {
        access.require(actorId);
        if(ids==null || ids.isEmpty() || ids.size()>100 || ids.stream().anyMatch(id -> id==null || id<=0))
            throw new IllegalArgumentException("対象IDを1件から100件で指定してください。");
        return ids.stream().distinct().map(id -> {
            access.auditRead(actorId,"WITHDRAWAL_MIGRATION",id.toString(),"PREVIEW");
            return users.findById(id).map(user -> {
                String status;
                if(user.getAccountStatus()!=AccountStatus.WITHDRAWN) status="NOT_WITHDRAWN";
                else if(user.getPersonalDataErasedAt()!=null) status="ALREADY_ERASED";
                else if(users.findByEmail(WithdrawalIdentity.email(id)).filter(other -> !other.getId().equals(id)).isPresent()
                        || users.findByUsername(WithdrawalIdentity.username(id)).filter(other -> !other.getId().equals(id)).isPresent()) status="COLLISION";
                else if(WithdrawalIdentity.reservedEmail(user.getEmail()) || WithdrawalIdentity.reservedUsername(user.getUsername())) status="REVIEW_REQUIRED";
                else status="REQUIRES_RETENTION_REVIEW";
                return new Preview(id,status);
            }).orElse(new Preview(id,"MISSING"));
        }).toList();
    }
}
