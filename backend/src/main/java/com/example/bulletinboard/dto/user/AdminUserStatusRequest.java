package com.example.bulletinboard.dto.user;

import java.io.IOException;

import com.example.bulletinboard.model.AccountStatus;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;

/** 凍結または凍結解除後の状態を明示する。 */
public record AdminUserStatusRequest(@NotNull @JsonDeserialize(using = StatusDeserializer.class)
        AccountStatus status) {
    /** Enumの序数や曖昧な表記を受け付けない。 */
    public static class StatusDeserializer extends JsonDeserializer<AccountStatus> {
        @Override
        public AccountStatus deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                return context.reportInputMismatch(AccountStatus.class, "状態名の文字列を指定してください。");
            }
            try {
                return AccountStatus.valueOf(parser.getText());
            } catch (IllegalArgumentException ex) {
                return context.reportInputMismatch(AccountStatus.class, "指定された状態は無効です。");
            }
        }
    }
}
