package com.example.bulletinboard.dto.contact;

import java.io.IOException;

import com.example.bulletinboard.model.ContactStatus;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import jakarta.validation.constraints.NotNull;

/**
 *【クラスの役割】
 * 管理者によるお問い合わせの状態変更を受け取るDTOです。
 *
 * statusはContactStatusのEnum名と完全一致する文字列を受け付けます。
 * 数値・空文字・空白・未知の値はJSON変換時に拒否します。
 * 未指定・nullは、APIの@Validと@NotNullで拒否します。
 *
 * @param status 変更先の対応状態
 */
public record AdminContactStatusRequest(

        @NotNull(message = "ステータスを指定してください。")
        @JsonDeserialize(using = StatusDeserializer.class)
        ContactStatus status

) {

    /**
     * このDTOのstatusに限定したJSON変換処理です。
     */
    public static class StatusDeserializer
            extends JsonDeserializer<ContactStatus> {

        @Override
        public ContactStatus deserialize(
                JsonParser parser,
                DeserializationContext context) throws IOException {

            // 数値などをEnumへ自動変換せず、文字列だけを受け付ける。
            if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                return context.reportInputMismatch(
                        ContactStatus.class,
                        "statusには状態名の文字列を指定してください。"
                );
            }

            // 空白除去や大文字変換は行わず、Enum名と完全一致させる。
            try {
                return ContactStatus.valueOf(parser.getText());
            } catch (IllegalArgumentException ex) {
                return context.reportInputMismatch(
                        ContactStatus.class,
                        "statusに指定された状態名は無効です。"
                );
            }
        }
    }
}
