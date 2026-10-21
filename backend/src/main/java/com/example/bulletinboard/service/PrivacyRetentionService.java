package com.example.bulletinboard.service;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.bulletinboard.model.*;
import com.example.bulletinboard.model.PrivacyRetentionRecord.Target;
import com.example.bulletinboard.repository.*;
import com.example.bulletinboard.exception.ForbiddenOperationException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.*;

@Service
public class PrivacyRetentionService {
    public enum IdentityField { EMAIL, USERNAME }
    public record HoldRequest(@NotNull Target targetType, @NotBlank @Size(max=36) String targetId,
            @NotBlank @Size(max=500) String purpose, @NotBlank @Size(max=500) String basis,
            @NotNull @Future OffsetDateTime expiresAt, Set<IdentityField> identityFields) { }
    public record View(Long id, Long userId, Target targetType, String targetId, Long reportId,
            String purpose, String basis, LocalDateTime expiresAt, String accessScope, String status) {
        static View from(PrivacyRetentionRecord r) { return new View(r.getId(),r.getUserId(),r.getTargetType(),
                r.getTargetId(),r.getReportId(),r.getPurpose(),r.getBasis(),r.getExpiresAt(),r.getAccessScope(),r.getStatus()); }
    }
    private final PrivacyRetentionRepository records;
    private final UserRepository users;
    private final ProfileImageRepository images;
    private final ReportRepository reports;
    private final PrivacyAccessService access;
    private final PrivacyCrypto crypto;
    private final ObjectMapper mapper;
    private final jakarta.persistence.EntityManager entityManager;
    public PrivacyRetentionService(PrivacyRetentionRepository records,UserRepository users,
            ProfileImageRepository images,ReportRepository reports,PrivacyAccessService access,
            PrivacyCrypto crypto,ObjectMapper mapper,jakarta.persistence.EntityManager entityManager) {
        this.records=records;this.users=users;this.images=images;this.reports=reports;
        this.access=access;this.crypto=crypto;this.mapper=mapper;
        this.entityManager=entityManager;
    }
    public static LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
    private String aad(PrivacyRetentionRecord r) { return r.getUserId()+":"+r.getTargetType()+":"+r.getTargetId()+":"+r.getId(); }

    @Transactional
    public View create(HoldRequest request,String actorId) {
        User actor=access.require(actorId);
        validate(request);
        Long owner=owner(request.targetType(),request.targetId());
        User user=users.findByIdForUpdate(owner).orElseThrow(() -> new IllegalArgumentException("対象が存在しません。"));
        entityManager.refresh(user,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        PrivacyRetentionRecord record=new PrivacyRetentionRecord();
        record.setUserId(owner);record.setTargetType(request.targetType());record.setTargetId(request.targetId());
        if(request.targetType()==Target.REPORT_EVIDENCE) record.setReportId(Long.valueOf(request.targetId()));
        record.setCreatedBy(actor.getId());applyPolicy(record,request);
        records.saveAndFlush(record);
        if(record.getTargetType()==Target.USER_IDENTITY) {
            if(user.getPersonalDataErasedAt()!=null) throw new IllegalArgumentException("元情報は保存できません。");
            capture(record,user);
        }
        records.saveAndFlush(record);
        access.audit(actorId,"RETENTION",record.getId().toString(),"CREATE");
        return View.from(record);
    }

    @Transactional
    public View setPolicy(Long id,HoldRequest request,String actorId) {
        access.require(actorId);validate(request);
        PrivacyRetentionRecord record=lockRecord(id);
        if(record.getTargetType()!=request.targetType() || !record.getTargetId().equals(request.targetId())
                || "PURGED".equals(record.getStatus())) throw new IllegalArgumentException("対象は変更できません。");
        if(record.getTargetType()==Target.USER_IDENTITY && !Objects.equals(record.getIdentityFields(),fields(request)))
            throw new IllegalArgumentException("保存項目の変更には新しい保持記録が必要です。");
        applyPolicy(record,request);records.saveAndFlush(record);
        access.audit(actorId,"RETENTION",id.toString(),"SET_POLICY");return View.from(record);
    }

    @Transactional
    public View release(Long id,String actorId) {
        access.require(actorId);
        PrivacyRetentionRecord record=lockRecord(id);
        if("POLICY_PENDING".equals(record.getStatus())) throw new IllegalArgumentException("保持方針を先に設定してください。");
        if(!"PURGED".equals(record.getStatus())) record.setStatus("RELEASED");
        record.setUpdatedAt(now());records.saveAndFlush(record);
        access.audit(actorId,"RETENTION",id.toString(),"RELEASE");return View.from(record);
    }

    @Transactional
    public View purgePayload(Long id,String actorId) {
        access.require(actorId);
        PrivacyRetentionRecord record=lockRecord(id);
        if(record.getTargetType()!=Target.USER_IDENTITY || record.holdsAt(now())) throw new IllegalArgumentException("保持対象は削除できません。");
        record.setEncryptedPayload(null);record.setKeyId(null);record.setStatus("PURGED");record.setUpdatedAt(now());
        records.saveAndFlush(record);access.audit(actorId,"RETENTION",id.toString(),"PURGE_PAYLOAD");return View.from(record);
    }

    @Transactional(readOnly=true)
    public View detail(Long id,String actorId) {
        access.auditRead(actorId,"RETENTION",id.toString(),"READ_METADATA");
        return View.from(records.findById(id).orElseThrow(() -> new IllegalArgumentException("記録が存在しません。")));
    }

    @Transactional(readOnly=true)
    public Map<String,String> identity(Long id,String actorId) {
        access.require(actorId);
        PrivacyRetentionRecord record=records.findById(id).orElseThrow(() -> new IllegalArgumentException("記録が存在しません。"));
        if(record.getTargetType()!=Target.USER_IDENTITY || !"ACTIVE".equals(record.getStatus()) || !record.holdsAt(now()))
            throw new ForbiddenOperationException("この情報は閲覧できません。");
        access.auditRead(actorId,"RETENTION",id.toString(),"READ_IDENTITY");
        try { return mapper.readValue(crypto.decrypt(record.getEncryptedPayload(),aad(record),record.getKeyId()),new TypeReference<Map<String,String>>(){}); }
        catch(java.io.IOException ex) { throw new IllegalStateException("Privacy payload unavailable"); }
    }

    /** 呼び出し元は対象Userをロック済み。元情報を通常Userへ戻す経路を作らない。 */
    public void preserveBeforeErasure(User user) {
        for(var record:records.findByTargetTypeAndTargetId(Target.USER_IDENTITY,user.getId().toString())) {
            if(record.holdsAt(now())) {
                if(!"ACTIVE".equals(record.getStatus())) throw new IllegalStateException("Identity policy pending");
                capture(record,user);records.saveAndFlush(record);
            }
        }
    }
    private void capture(PrivacyRetentionRecord record,User user) {
        crypto.requireKey(crypto.keyId());
        Map<String,String> value=new LinkedHashMap<>();
        Set<String> fields=Set.of(record.getIdentityFields().split(","));
        if(fields.contains("EMAIL")) value.put("email",user.getEmail());
        if(fields.contains("USERNAME")) value.put("username",user.getUsername());
        try { record.setEncryptedPayload(crypto.encrypt(mapper.writeValueAsBytes(value),aad(record))); }
        catch(java.io.IOException ex) { throw new IllegalStateException("Privacy payload unavailable"); }
        record.setKeyId(crypto.keyId());record.setUpdatedAt(now());
    }
    public void pending(Target type,String targetId,Long owner,Long reportId) {
        if(records.findByTargetTypeAndTargetId(type,targetId).isEmpty()) {
            PrivacyRetentionRecord record=new PrivacyRetentionRecord();record.setUserId(owner);
            record.setTargetType(type);record.setTargetId(targetId);record.setReportId(reportId);
            record.setCreatedAt(now());record.setUpdatedAt(now());records.saveAndFlush(record);
        }
    }
    public boolean held(Target type,String targetId) {
        return records.findByTargetTypeAndTargetId(type,targetId).stream().anyMatch(r -> r.holdsAt(now()));
    }
    public void requireEvidencePolicy(Long reportId) {
        var policies=records.findByTargetTypeAndTargetId(Target.REPORT_EVIDENCE,reportId.toString());
        if(policies.isEmpty() || policies.stream().noneMatch(r -> r.holdsAt(now())))
            throw new ForbiddenOperationException("証拠の保持方針を確認してください。");
    }
    private void validate(HoldRequest r) {
        if(r==null || r.targetType()==null || r.targetId()==null || r.targetId().length()>36
                || r.purpose()==null || r.purpose().isBlank() || r.purpose().length()>500
                || r.basis()==null || r.basis().isBlank() || r.basis().length()>500
                || r.expiresAt()==null || !r.expiresAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC)))
            throw new IllegalArgumentException("保持条件を確認してください。");
        if(r.targetType()==Target.USER_IDENTITY && (r.identityFields()==null || r.identityFields().isEmpty()))
            throw new IllegalArgumentException("保持する項目を指定してください。");
        if(r.targetType()!=Target.USER_IDENTITY && r.identityFields()!=null && !r.identityFields().isEmpty())
            throw new IllegalArgumentException("この対象に本人情報は保存できません。");
    }
    private String fields(HoldRequest r) {
        return r.identityFields()==null ? null : r.identityFields().stream().sorted().map(Enum::name).collect(java.util.stream.Collectors.joining(","));
    }
    private void applyPolicy(PrivacyRetentionRecord record,HoldRequest r) {
        record.setPurpose(r.purpose());record.setBasis(r.basis());
        record.setExpiresAt(r.expiresAt().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime());
        record.setIdentityFields(fields(r));record.setStatus("ACTIVE");record.setUpdatedAt(now());
    }
    private Long owner(Target type,String targetId) {
        return switch(type) {
            case USER_IDENTITY -> targetNumber(targetId);
            case PROFILE_IMAGE -> images.findById(targetId).orElseThrow(() -> new IllegalArgumentException("画像が存在しません。")).getOwnerUserId();
            case REPORT_EVIDENCE -> {
                Report report=reports.findById(targetNumber(targetId))
                        .orElseThrow(() -> new IllegalArgumentException("通報が存在しません。"));
                if(report.getTargetOwnerUserId()==null) throw new IllegalArgumentException("対象所有者の確認が必要です。");
                yield report.getTargetOwnerUserId();
            }
        };
    }
    private Long targetNumber(String value) {
        try {
            if(!value.matches("[1-9][0-9]{0,18}")) throw new NumberFormatException();
            return Long.valueOf(value);
        } catch(NumberFormatException ex) { throw new IllegalArgumentException("対象IDを確認してください。"); }
    }
    private PrivacyRetentionRecord lockRecord(Long id) {
        var initial=records.findById(id).orElseThrow(() -> new IllegalArgumentException("記録が存在しません。"));
        users.findByIdForUpdate(initial.getUserId()).orElseThrow();
        var record=records.lock(id).orElseThrow();
        entityManager.refresh(record,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return record;
    }
}
