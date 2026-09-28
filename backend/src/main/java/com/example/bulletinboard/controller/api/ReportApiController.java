package com.example.bulletinboard.controller.api;

import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.bulletinboard.dto.report.ReportSubmittedResponse;
import com.example.bulletinboard.dto.report.TopicReportRequest;
import com.example.bulletinboard.exception.ReportOperationException;
import com.example.bulletinboard.exception.ReportOperationException.Reason;
import com.example.bulletinboard.service.ReportService;
import com.example.bulletinboard.service.ReportSubmissionService;

import jakarta.validation.Valid;

/** お題通報受付と管理者限定の証拠画像取得。一般向け応答に通報者・内容を含めない。 */
@RestController
public class ReportApiController {
    private final ReportSubmissionService submission;
    private final ReportService reports;

    public ReportApiController(ReportSubmissionService submission, ReportService reports) {
        this.submission = submission;
        this.reports = reports;
    }

    @PostMapping("/api/topics/{topicId}/reports")
    public ResponseEntity<ReportSubmittedResponse> reportTopic(@PathVariable Long topicId,
            @Valid @RequestBody TopicReportRequest request, Authentication authentication) {
        try {
            submission.submitTopic(topicId, authentication.getName(), request);
            return ResponseEntity.status(201).body(new ReportSubmittedResponse("通報を受け付けました。"));
        } catch (DataAccessException | TransactionException ex) {
            throw new ReportOperationException(Reason.FAILED, topicId, ex);
        }
    }

    @GetMapping("/api/admin/reports/{reportId}/evidence-image")
    public ResponseEntity<byte[]> getTopicEvidenceImage(@PathVariable Long reportId, Authentication authentication) {
        try {
            var evidence = reports.getTopicEvidenceImage(reportId, authentication.getName());
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .contentType(MediaType.parseMediaType(evidence.contentType()))
                    .contentLength(evidence.bytes().length)
                    .header("X-Content-Type-Options", "nosniff")
                    .header("Content-Security-Policy", "default-src 'none'; sandbox")
                    .body(evidence.bytes());
        } catch (DataAccessException | TransactionException ex) {
            throw new ReportOperationException(Reason.FAILED, reportId, ex);
        }
    }
}
