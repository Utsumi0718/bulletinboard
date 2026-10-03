package com.example.bulletinboard.dto.report;

/** 通報者・対象所有者・証拠内容を一般向け応答へ含めない。 */
public record ReportSubmittedResponse(String message) {
}
