package com.example.bulletinboard.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.example.bulletinboard.exception.TopicImageException;
import com.example.bulletinboard.exception.TopicImageException.Reason;

/** ファイル名・申告Content-Typeを信用せず、画像本体を読み取って検証する。 */
@Component
public class TopicImageValidator {
    // 5MBは5,000,000バイトとして統一する。推奨寸法は必須条件にしない。
    public static final int MAX_BYTES = 5_000_000;

    public record ValidatedImage(byte[] bytes, String contentType) {
        public ValidatedImage {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }

    public ValidatedImage validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new TopicImageException(Reason.INVALID);
        }
        if (file.getSize() > MAX_BYTES) {
            throw new TopicImageException(Reason.TOO_LARGE);
        }
        byte[] bytes;
        try (var input = file.getInputStream()) {
            // getSizeの申告値に依存せず、実際に読み込む量も制限する。
            bytes = input.readNBytes(MAX_BYTES + 1);
        } catch (IOException ex) {
            throw new TopicImageException(Reason.FAILED, ex);
        }
        if (bytes.length > MAX_BYTES) {
            throw new TopicImageException(Reason.TOO_LARGE);
        }
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new TopicImageException(Reason.INVALID);
            }
            ImageReader reader = readers.next();
            try {
                String mime = switch (reader.getFormatName().toLowerCase(Locale.ROOT)) {
                    case "jpeg", "jpg" -> "image/jpeg";
                    case "png" -> "image/png";
                    case "webp" -> "image/webp";
                    default -> throw new TopicImageException(Reason.INVALID);
                };
                reader.setInput(input, false, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0) {
                    throw new TopicImageException(Reason.INVALID);
                }
                // 検証用の展開画像を縮小して読み込む。保存する原本は変換しない。
                var parameters = reader.getDefaultReadParam();
                parameters.setSourceSubsampling(
                        Math.max(1, (width - 1) / 1024 + 1),
                        Math.max(1, (height - 1) / 1024 + 1), 0, 0);
                boolean[] warned = {false};
                reader.addIIOReadWarningListener((source, warning) -> warned[0] = true);
                var decoded = reader.read(0, parameters);
                if (decoded == null || warned[0]) {
                    throw new TopicImageException(Reason.INVALID);
                }
                decoded.flush();
                return new ValidatedImage(bytes, mime);
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException ex) {
            throw new TopicImageException(Reason.INVALID, ex);
        }
    }
}
