package com.example.bulletinboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.bulletinboard.exception.TopicImageException;
import com.example.bulletinboard.exception.TopicImageException.Reason;
import com.example.bulletinboard.model.AccountStatus;
import com.example.bulletinboard.model.TopicImage;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.repository.TopicImageRepository;
import com.example.bulletinboard.repository.TopicRepository;
import com.example.bulletinboard.repository.UserRepository;
import com.example.bulletinboard.service.TopicImageValidator.ValidatedImage;

@ExtendWith(MockitoExtension.class)
class TopicImageServiceTest {
    private static final String EMAIL = "owner@example.com";
    private static final String ID = "11111111-1111-1111-1111-111111111111";
    private static final String URL = TopicImageService.URL_PREFIX + ID;
    @Mock TopicImageRepository images;
    @Mock UserRepository users;
    @Mock TopicRepository topics;
    @InjectMocks TopicImageService service;

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_USER", "ROLE_ADMIN"})
    void activeUserCanUploadAndOwnershipComesFromDatabase(String role) {
        User owner = active();
        owner.setRole(role);
        when(images.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var response = service.upload(new ValidatedImage(new byte[]{1, 2}, "image/png"), EMAIL);
        var capture = ArgumentCaptor.forClass(TopicImage.class);
        verify(images).save(capture.capture());
        assertThat(capture.getValue().getOwnerUserId()).isEqualTo(7L);
        assertThat(capture.getValue().getContent()).containsExactly(1, 2);
        assertThat(response.url()).isEqualTo(TopicImageService.URL_PREFIX + capture.getValue().getId());
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"FROZEN", "WITHDRAWN"})
    void rejectsInactiveUserEvenWithExistingAuthentication(AccountStatus state) {
        active().setAccountStatus(state);
        expectReason(() -> service.upload(new ValidatedImage(new byte[]{1}, "image/png"), EMAIL), Reason.FORBIDDEN);
        verify(images, never()).save(any());
    }

    @Test
    void rejectsMissingAuthenticatedUser() {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());
        expectReason(() -> service.requireOwnedImage(URL, EMAIL), Reason.FORBIDDEN);
    }

    @Test
    void allowsOwnerToAttachImage() {
        active();
        when(images.existsByIdAndOwnerUserId(ID, 7L)).thenReturn(true);
        service.requireOwnedImage(URL, EMAIL);
        verify(images, never()).findById(any());
    }

    @Test
    void cannotAttachUnownedOrMissingImage() {
        active();
        expectReason(() -> service.requireOwnedImage(URL, EMAIL), Reason.INVALID_REFERENCE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://example.com/image.png", "../../private.png",
            "/api/topic-images/11111111-1111-1111-1111-111111111111?other=1"})
    void rejectsUnmanagedReferencesWithoutFetchingThem(String url) {
        active();
        expectReason(() -> service.requireOwnedImage(url, EMAIL), Reason.INVALID_REFERENCE);
        verify(images, never()).findById(any());
    }

    @Test
    void hidesUnpublishedImageFromOtherUser() {
        active();
        expectReason(() -> service.get(ID, EMAIL), Reason.NOT_FOUND);
        verify(images, never()).findById(any());
    }

    @Test
    void readsImageReferencedByUndeletedTopic() {
        active();
        var image = new TopicImage(8L, "image/png", new byte[]{9});
        when(topics.existsByImageAndDeletedAtIsNull(URL)).thenReturn(true);
        when(images.findById(ID)).thenReturn(Optional.of(image));
        assertThat(service.get(ID, EMAIL).getContent()).containsExactly(9);
    }

    private User active() {
        User user = new User();
        user.setId(7L);
        user.setEmail(EMAIL);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        return user;
    }

    private void expectReason(Runnable action, Reason reason) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(TopicImageException.class,
                ex -> assertThat(ex.getReason()).isEqualTo(reason));
    }
}
