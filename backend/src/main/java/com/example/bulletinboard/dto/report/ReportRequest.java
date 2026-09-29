package com.example.bulletinboard.dto.report;

import java.io.IOException;

import com.example.bulletinboard.model.ReportReason;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 対象IDはURL、通報者は認証情報から確定する。所有者IDは受け取らない。 */
public record ReportRequest(
        @NotNull(message = "通報理由を指定してください")
        @JsonDeserialize(using = ReasonDeserializer.class) ReportReason reason,
        @Size(max = 500, message = "詳細は500文字以内で入力してください") String detail) {

    /** 数値をEnumの順序として解釈せず、識別子の完全一致のみ受け付ける。 */
    public static class ReasonDeserializer extends JsonDeserializer<ReportReason> {
        @Override
        public ReportReason deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                return context.reportInputMismatch(ReportReason.class, "reasonには理由の識別子を指定してください。");
            }
            try {
                return ReportReason.valueOf(parser.getText());
            } catch (IllegalArgumentException ex) {
                return context.reportInputMismatch(ReportReason.class, "reasonには有効な識別子を指定してください。");
            }
        }
    }
}
