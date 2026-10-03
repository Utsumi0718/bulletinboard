package com.example.bulletinboard.controller.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.TransactionSystemException;

import com.example.bulletinboard.dto.topic.TopicImageResponse;
import com.example.bulletinboard.exception.handler.GlobalExceptionHandler;
import com.example.bulletinboard.model.TopicImage;
import com.example.bulletinboard.repository.UserRepository;
import com.example.bulletinboard.security.SecurityConfig;
import com.example.bulletinboard.service.TopicImageService;
import com.example.bulletinboard.service.TopicImageValidator;
import com.example.bulletinboard.service.TopicImageValidator.ValidatedImage;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

@WebMvcTest(TopicImageApiController.class)
@Import(SecurityConfig.class)
class TopicImageApiControllerTest {
    private static final String BASE = "/api/topic-images";
    private static final String ID = "11111111-1111-1111-1111-111111111111";
    private static final String EMAIL = "owner@example.com";
    @Autowired MockMvc mvc;
    @MockitoBean TopicImageService service;
    @MockitoBean TopicImageValidator validator;
    @MockitoBean UserRepository users;

    @Test
    void uploadReturnsOnlyUrlAfterServiceReturns() throws Exception {
        validated();
        when(service.upload(any(), eq(EMAIL))).thenReturn(new TopicImageResponse(BASE + "/" + ID));
        mvc.perform(multipart(BASE).file(file()).with(user(EMAIL)).with(csrf()))
                .andExpect(status().isCreated()).andExpect(header().string("Location", BASE + "/" + ID))
                .andExpect(content().json("{\"url\":\"" + BASE + "/" + ID + "\"}", true));
    }

    @Test
    void unauthenticatedUploadIsRejected() throws Exception {
        mvc.perform(multipart(BASE).file(file()).with(csrf())).andExpect(status().isUnauthorized());
        verifyNoInteractions(service, validator);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingOrInvalidCsrfIsRejected(boolean invalid) throws Exception {
        var request = multipart(BASE).file(file()).with(user(EMAIL));
        if (invalid) request.with(csrf().useInvalidToken());
        mvc.perform(request).andExpect(status().isForbidden());
        verifyNoInteractions(service, validator);
    }

    @Test
    void missingFileReturnsCommon400() throws Exception {
        mvc.perform(multipart(BASE).with(user(EMAIL)).with(csrf())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.path").value(BASE));
        verifyNoInteractions(service, validator);
    }

    @Test
    void getReturnsSafeHeadersAndExactBytes() throws Exception {
        when(service.get(ID, EMAIL)).thenReturn(new TopicImage(7L, "image/png", new byte[]{1, 2}));
        mvc.perform(get(BASE + "/" + ID).with(user(EMAIL))).andExpect(status().isOk())
                .andExpect(content().bytes(new byte[]{1, 2})).andExpect(content().contentType("image/png"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void getRequiresAuthentication() throws Exception {
        mvc.perform(get(BASE + "/" + ID)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void storageFailuresHaveFixedResponseAndOneSafeLog(boolean upload) throws Exception {
        String secret = "SELECT secret FROM images fake@example.com fake-secret";
        RuntimeException failure = upload ? new DataAccessResourceFailureException(secret)
                : new TransactionSystemException(secret);
        if (upload) {
            validated();
            when(service.upload(any(), eq(EMAIL))).thenThrow(failure);
        } else {
            when(service.get(ID, EMAIL)).thenThrow(failure);
        }
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var request = upload ? multipart(BASE).file(file()).with(user(EMAIL)).with(csrf())
                    : get(BASE + "/" + ID).with(user(EMAIL));
            var result = mvc.perform(request).andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.error").value("Internal Server Error"))
                    .andExpect(jsonPath("$.message").value("画像の処理に失敗しました。時間をおいて再度お試しください。"))
                    .andExpect(jsonPath("$.path").value(upload ? BASE : BASE + "/" + ID)).andReturn();
            assertThat(result.getResponse().getContentAsString()).doesNotContain("SELECT", "fake@example.com", "fake-secret");
            List<ILoggingEvent> errors = appender.list.stream().filter(event -> event.getLevel() == Level.ERROR).toList();
            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst().getFormattedMessage()).isEqualTo("topic_image_error errorType=" + failure.getClass().getSimpleName());
            assertThat(errors.getFirst().getThrowableProxy()).isNull();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "image.png", "image/png", new byte[]{1, 2});
    }

    private void validated() {
        when(validator.validate(any())).thenReturn(new ValidatedImage(new byte[]{1, 2}, "image/png"));
    }
}
