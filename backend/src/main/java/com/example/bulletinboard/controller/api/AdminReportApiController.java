package com.example.bulletinboard.controller.api;

import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.bulletinboard.dto.common.PageResponse;
import com.example.bulletinboard.dto.report.AdminReportDetailResponse;
import com.example.bulletinboard.dto.report.AdminReportListResponse;
import com.example.bulletinboard.dto.report.AdminReportStatusRequest;
import com.example.bulletinboard.exception.AdminReportOperationException;
import com.example.bulletinboard.exception.AdminReportOperationException.Operation;
import com.example.bulletinboard.model.ReportStatus;
import com.example.bulletinboard.service.AdminReportService;

import jakarta.validation.Valid;

/** /api/admin/** の認可に加え、管理Serviceでも管理者の現在状態を確認する。 */
@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportApiController {
    private final AdminReportService service;

    public AdminReportApiController(AdminReportService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<PageResponse<AdminReportListResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long reporterUserId,
            Authentication authentication) {
        ReportStatus parsed = null;
        if (status != null) {
            try {
                parsed = ReportStatus.valueOf(status);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("指定されたステータスは無効です。");
            }
        }
        try {
            Page<AdminReportListResponse> result = service.list(page, size, parsed, reporterUserId,
                    authentication.getName());
            return ResponseEntity.ok(PageResponse.from(result));
        } catch (DataAccessException | TransactionException ex) {
            throw new AdminReportOperationException(Operation.LIST, null, ex);
        }
    }

    @GetMapping("/{reportId}")
    public ResponseEntity<AdminReportDetailResponse> detail(@PathVariable Long reportId,
            Authentication authentication) {
        try {
            return ResponseEntity.ok(service.detail(reportId, authentication.getName()));
        } catch (DataAccessException | TransactionException ex) {
            throw new AdminReportOperationException(Operation.DETAIL, reportId, ex);
        }
    }

    @PatchMapping("/{reportId}/status")
    public ResponseEntity<AdminReportDetailResponse> updateStatus(@PathVariable Long reportId,
            @Valid @RequestBody AdminReportStatusRequest request, Authentication authentication) {
        try {
            return ResponseEntity.ok(service.updateStatus(reportId, request.status(), authentication.getName()));
        } catch (DataAccessException | TransactionException ex) {
            throw new AdminReportOperationException(Operation.STATUS_CHANGE, reportId, ex);
        }
    }
}
