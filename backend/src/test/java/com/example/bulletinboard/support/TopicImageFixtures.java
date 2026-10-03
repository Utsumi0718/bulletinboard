package com.example.bulletinboard.support;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

import javax.imageio.ImageIO;

/** 外部画像・個人情報を使わない、テスト内生成画像。WebPは1ピクセルの固定データ。 */
public final class TopicImageFixtures {
    private TopicImageFixtures() { }

    public static byte[] image(String format) {
        if (format.equals("webp")) {
            return Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA");
        }
        try {
            var output = new ByteArrayOutputStream();
            var image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
            image.setRGB(1, 1, 0xFF8844);
            if (!ImageIO.write(image, format, output)) {
                throw new IllegalStateException("Missing fixture writer");
            }
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
