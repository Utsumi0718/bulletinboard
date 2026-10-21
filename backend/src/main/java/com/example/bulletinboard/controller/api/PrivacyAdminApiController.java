package com.example.bulletinboard.controller.api;

import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import com.example.bulletinboard.service.*;
import com.example.bulletinboard.repository.WithdrawalTaskRepository;
import com.example.bulletinboard.model.WithdrawalTask;

/** 通常管理APIから分離。ServiceでもDB上の専用権限を確認し、秘密情報を記録しない。 */
@RestController @RequestMapping("/api/admin/privacy")
public class PrivacyAdminApiController {
    private final PrivacyRetentionService retention;
    private final PrivacyAccessService access;
    private final AdminReportService reports;
    private final WithdrawalFollowupService followup;
    private final WithdrawalTaskRepository tasks;
    private final WithdrawalMigrationPlanService migration;
    public PrivacyAdminApiController(PrivacyRetentionService retention,PrivacyAccessService access,
            AdminReportService reports,WithdrawalFollowupService followup,WithdrawalTaskRepository tasks,WithdrawalMigrationPlanService migration) {
        this.retention=retention;this.access=access;this.reports=reports;this.followup=followup;this.tasks=tasks;
        this.migration=migration;
    }
    @PostMapping("/migration-preview")
    public ResponseEntity<?> preview(@RequestBody java.util.List<Long> ids,Authentication auth) {
        return response(migration.preview(ids,auth.getName()));
    }
    private <T> ResponseEntity<T> response(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
    @PostMapping("/retentions")
    public ResponseEntity<?> create(@Valid @RequestBody PrivacyRetentionService.HoldRequest request,Authentication auth) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(retention.create(request,auth.getName()));
    }
    @PutMapping("/retentions/{id}")
    public ResponseEntity<?> policy(@PathVariable Long id,@Valid @RequestBody PrivacyRetentionService.HoldRequest request,Authentication auth) {
        return response(retention.setPolicy(id,request,auth.getName()));
    }
    @GetMapping("/retentions/{id}")
    public ResponseEntity<?> detail(@PathVariable Long id,Authentication auth) { return response(retention.detail(id,auth.getName())); }
    @GetMapping("/retentions/{id}/identity")
    public ResponseEntity<?> identity(@PathVariable Long id,Authentication auth) { return response(retention.identity(id,auth.getName())); }
    @PostMapping("/retentions/{id}/release")
    public ResponseEntity<?> release(@PathVariable Long id,Authentication auth) { return response(retention.release(id,auth.getName())); }
    @DeleteMapping("/retentions/{id}/identity")
    public ResponseEntity<?> purge(@PathVariable Long id,Authentication auth) { return response(retention.purgePayload(id,auth.getName())); }
    @GetMapping("/reports/{id}/evidence")
    public ResponseEntity<?> evidence(@PathVariable Long id,Authentication auth) { return response(reports.evidenceDetail(id,auth.getName())); }
    public record TaskView(Long userId,String sessionStatus,String imageStatus,int sessionAttempts,int imageAttempts,String lastErrorType) {
        static TaskView from(WithdrawalTask task) { return new TaskView(task.getUserId(),task.getSessionStatus(),task.getImageStatus(),task.getSessionAttempts(),task.getImageAttempts(),task.getLastErrorType()); }
    }
    @PostMapping("/withdrawal-tasks/{id}/retry")
    public ResponseEntity<?> retry(@PathVariable Long id,Authentication auth) {
        access.audit(auth.getName(),"WITHDRAWAL_TASK",id.toString(),"RETRY");
        followup.processSessions(id);followup.processImages(id);
        return response(TaskView.from(tasks.findById(id).orElseThrow(() -> new IllegalArgumentException("対象が存在しません。"))));
    }
}
