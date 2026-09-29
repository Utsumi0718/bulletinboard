package com.example.bulletinboard.dto.report;

/** 通報時点の文章。画像本体は管理者専用の別APIから取得する。 */
public record AdminReportEvidenceResponse(
        String topicTitle,
        String topicQuestion,
        String answerContent,
        Long parentTopicId,
        String parentTopicTitle,
        String parentTopicQuestion,
        String profileDisplayName,
        String profileBio,
        boolean imageAvailable,
        String evidenceImageUrl) {
}
