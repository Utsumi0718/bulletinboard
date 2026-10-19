package com.example.bulletinboard.dto.contact;

import java.time.LocalDateTime;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 保持設定時はServiceでreasonと将来のuntilを必須検証する。解除はheld=false。 */
public record ContactLegalHoldRequest(
        @NotNull(message = "保持の設定を指定してください") Boolean held,
        @Size(max = 500, message = "保持理由は500文字以内で指定してください") String reason,
        LocalDateTime until) { }
