package com.example.bulletinboard.controller.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.example.bulletinboard.cleanup.CleanupAdminService;
import com.example.bulletinboard.dto.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/admin/topic-cleanup")
public class AdminTopicCleanupApiController {
    private static final Logger log=LoggerFactory.getLogger(AdminTopicCleanupApiController.class);
    private final CleanupAdminService service;
    public AdminTopicCleanupApiController(CleanupAdminService service) { this.service=service; }
    @GetMapping("/runs")
    public Object runs(Authentication auth,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.runs(auth.getName(),page,size);
    }
    @GetMapping("/targets")
    public Object targets(Authentication auth,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) String state) {
        return service.targets(auth.getName(),page,size,state);
    }
    @GetMapping("/targets/{id}/attempts")
    public Object attempts(Authentication auth,@PathVariable long id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.attempts(auth.getName(),id,page,size);
    }
    @GetMapping("/dry-run")
    public Object dryRun(Authentication auth,@RequestParam(defaultValue="100") int size) { return service.dryRun(auth.getName(),size); }
    public record ResumeRequest(String reason) {
        @Override public String toString() { return "ResumeRequest[redacted]"; }
    }
    @PostMapping("/targets/{id}/resume")
    public ResponseEntity<Void> resume(Authentication auth,@PathVariable long id,@RequestBody ResumeRequest request) {
        service.resume(auth.getName(),id,request.reason()); return ResponseEntity.noContent().build();
    }
    @ExceptionHandler({DataAccessException.class,TransactionException.class})
    ResponseEntity<ErrorResponse> database(RuntimeException ex,HttpServletRequest request) {
        log.error("admin_topic_cleanup_error errorType={}",ex.getClass().getSimpleName());
        return ResponseEntity.status(500).body(new ErrorResponse(500,"Internal Server Error","処理を完了できませんでした。",request.getRequestURI()));
    }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ErrorResponse> state(ResponseStatusException ex,HttpServletRequest request) {
        var status=HttpStatus.valueOf(ex.getStatusCode().value());
        return ResponseEntity.status(status).body(new ErrorResponse(status.value(),status.getReasonPhrase(),
                status==HttpStatus.NOT_FOUND?"対象が見つかりません。":"現在の状態では操作できません。",request.getRequestURI()));
    }
}
