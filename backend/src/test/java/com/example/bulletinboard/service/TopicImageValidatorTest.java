package com.example.bulletinboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.example.bulletinboard.exception.TopicImageException;
import com.example.bulletinboard.exception.TopicImageException.Reason;
import com.example.bulletinboard.support.TopicImageFixtures;

class TopicImageValidatorTest {
    private final TopicImageValidator validator = new TopicImageValidator();

    @ParameterizedTest
    @ValueSource(strings = {"jpeg", "png", "webp"})
    void acceptsSupportedImageBytes(String format) {
        byte[] bytes = TopicImageFixtures.image(format);
        var result = validator.validate(file(bytes));
        assertThat(result.contentType()).isEqualTo("image/" + format);
        assertThat(result.bytes()).isEqualTo(bytes);
    }

    @Test
    void rejectsEmptyFile() {
        rejected(new byte[0], Reason.INVALID);
    }

    @Test
    void rejectsOverFiveMillionBytes() {
        rejected(new byte[TopicImageValidator.MAX_BYTES + 1], Reason.TOO_LARGE);
    }

    @Test
    void acceptsExactlyFiveMillionBytes() {
        byte[] bytes = Arrays.copyOf(TopicImageFixtures.image("png"), TopicImageValidator.MAX_BYTES);
        assertThat(validator.validate(file(bytes)).bytes()).hasSize(TopicImageValidator.MAX_BYTES);
    }

    @Test
    void usesActualFormatInsteadOfFilenameOrDeclaredContentType() {
        var file = new MockMultipartFile("file", "../../fake.svg", "text/html", TopicImageFixtures.image("png"));
        assertThat(validator.validate(file).contentType()).isEqualTo("image/png");
    }

    @Test
    void rejectsUnsupportedGif() {
        rejected(TopicImageFixtures.image("gif"), Reason.INVALID);
    }

    @Test
    void rejectsTruncatedPng() {
        rejected(Arrays.copyOf(TopicImageFixtures.image("png"), 35), Reason.INVALID);
    }

    @Test
    void wrapsStreamFailureWithoutPublicDetails() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(10L);
        when(file.getInputStream()).thenThrow(new IOException("private-path fake-secret"));
        assertThatThrownBy(() -> validator.validate(file)).isInstanceOfSatisfying(TopicImageException.class, ex -> {
            assertThat(ex.getReason()).isEqualTo(Reason.FAILED);
            assertThat(ex.getMessage()).doesNotContain("private-path", "fake-secret");
        });
    }

    private MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file", "image.bin", "application/octet-stream", bytes);
    }

    private void rejected(byte[] bytes, Reason reason) {
        assertThatThrownBy(() -> validator.validate(file(bytes)))
                .isInstanceOfSatisfying(TopicImageException.class, ex -> assertThat(ex.getReason()).isEqualTo(reason));
    }
}
