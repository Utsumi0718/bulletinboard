package com.example.bulletinboard.dto.report;

import java.io.IOException;

import com.example.bulletinboard.model.ReportStatus;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import jakarta.validation.constraints.NotNull;

/** Enum名と完全一致する文字列だけを受け付ける。 */
public record AdminReportStatusRequest(
        @NotNull(message = "ステータスを指定してください。")
        @JsonDeserialize(using = StatusDeserializer.class)
        ReportStatus status) {

    public static class StatusDeserializer extends JsonDeserializer<ReportStatus> {
        @Override
        public ReportStatus deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                return context.reportInputMismatch(ReportStatus.class, "状態名の文字列を指定してください。");
            }
            try {
                return ReportStatus.valueOf(parser.getText());
            } catch (IllegalArgumentException ex) {
                return context.reportInputMismatch(ReportStatus.class, "指定された状態は無効です。");
            }
        }
    }
}
