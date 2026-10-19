package com.example.bulletinboard.dto.contact;

import java.io.IOException;
import java.util.regex.Pattern;
import com.example.bulletinboard.model.ContactCategory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 【クラスの役割】
 * お問い合わせ受付APIの入力値を保持するDTO。
 * email/subjectはReactのtrim相当で正規化してから検証します。本文は入力を保持します。
 */
@Getter
@Setter
public class ContactRequest {

    // ECMAScript WhiteSpace + LineTerminator（全角空白・NBSP・BOMを含む）。
    public static final String WHITE_SPACE = "\\u0009-\\u000D\\u0020\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF";
    private static final Pattern EDGES = Pattern.compile("^[" + WHITE_SPACE + "]+|[" + WHITE_SPACE + "]+$");

    @NotNull(message = "お問い合わせ種別を選択してください")
    @JsonDeserialize(using = CategoryDeserializer.class)
    private ContactCategory category;

    @NotBlank(message = "メールアドレスを入力してください")
    @Email(message = "正しいメールアドレスの形式で入力してください")
    @Size(max = 255, message = "メールアドレスは255文字以内で入力してください")
    @jakarta.validation.constraints.Pattern(regexp = "^[^" + WHITE_SPACE + "]*$", message = "メールアドレスに空白を含めないでください")
    private String email;

    @NotBlank(message = "件名を入力してください")
    @Size(max = 100, message = "件名は100文字以内で入力してください")
    @jakarta.validation.constraints.Pattern(regexp = "^[^\\r\\n]*$", message = "件名に改行を含めないでください")
    private String subject;

    @NotNull(message = "お問い合わせ本文を入力してください")
    @Size(max = 1000, message = "お問い合わせ本文は1000文字以内で入力してください")
    @jakarta.validation.constraints.Pattern(regexp = "(?s).*[^" + WHITE_SPACE + "].*", message = "お問い合わせ本文を入力してください")
    private String message;

    public void setEmail(String email) { this.email = normalize(email); }
    public void setSubject(String subject) { this.subject = normalize(subject); }

    private static String normalize(String value) {
        return value == null ? null : EDGES.matcher(value).replaceAll("");
    }

    /** 数値からのEnum変換、小文字や前後空白を拒否する。 */
    public static class CategoryDeserializer extends JsonDeserializer<ContactCategory> {
        @Override
        public ContactCategory deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (parser.hasToken(JsonToken.VALUE_STRING)) {
                try { return ContactCategory.valueOf(parser.getText()); }
                catch (IllegalArgumentException ignored) { /* 入力値を公開メッセージへ含めない。 */ }
            }
            return context.reportInputMismatch(ContactCategory.class, "お問い合わせ種別が正しくありません。");
        }
    }
}
