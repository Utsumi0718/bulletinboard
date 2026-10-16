package com.example.bulletinboard.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Arrays;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import com.example.bulletinboard.exception.ProfileOperationException;
import com.example.bulletinboard.exception.ProfileOperationException.Reason;
import com.example.bulletinboard.support.TopicImageFixtures;

class ProfileImageValidatorTest {
    private final ProfileImageValidator validator = new ProfileImageValidator(new TopicImageValidator());

    @ParameterizedTest @ValueSource(strings = {"jpeg", "png", "webp"})
    void keepsExactOriginalBytesForSupportedFormats(String format) {
        byte[] bytes = TopicImageFixtures.image(format);
        var result = validator.validate(file(bytes));
        assertThat(result.bytes()).containsExactly(bytes);
        assertThat(result.contentType()).isEqualTo(format.equals("jpeg") ? "image/jpeg" : "image/" + format);
    }
    @Test void acceptsExactlyTwoMillionBytes() {
        assertThat(validator.validate(file(Arrays.copyOf(TopicImageFixtures.image("png"), 2_000_000))).bytes())
                .hasSize(2_000_000);
    }
    @Test void rejectsOneByteOverLimit() {
        rejected(file(new byte[2_000_001]), Reason.TOO_LARGE);
    }
    @ParameterizedTest @ValueSource(strings = {"empty", "gif", "truncated"})
    void rejectsUnreadableOrUnsupportedImages(String type) {
        byte[] bytes = switch (type) {
            case "empty" -> new byte[0];
            case "gif" -> TopicImageFixtures.image("gif");
            default -> Arrays.copyOf(TopicImageFixtures.image("png"), 35);
        };
        rejected(file(bytes), Reason.INVALID_IMAGE);
    }
    @Test void detectsFormatFromBytesInsteadOfUntrustedHeaders() {
        assertThat(validator.validate(new MockMultipartFile("file", "../../fake.svg", "text/html",
                TopicImageFixtures.image("png"))).contentType()).isEqualTo("image/png");
    }
    @Test void streamFailureDoesNotExposeDetails() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getInputStream()).thenThrow(new IOException("fake-secret-path"));
        rejected(file, Reason.FAILED);
    }
    private MockMultipartFile file(byte[] bytes) { return new MockMultipartFile("file", "file", "image/png", bytes); }
    private void rejected(MultipartFile file, Reason reason) {
        assertThatThrownBy(() -> validator.validate(file)).isInstanceOfSatisfying(ProfileOperationException.class,
                ex -> { assertThat(ex.getReason()).isEqualTo(reason); assertThat(ex.getMessage()).doesNotContain("fake-secret"); });
    }
}
