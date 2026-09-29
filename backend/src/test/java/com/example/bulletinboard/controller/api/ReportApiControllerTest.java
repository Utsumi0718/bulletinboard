package com.example.bulletinboard.controller.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.TransactionSystemException;

import com.example.bulletinboard.dto.report.ReportRequest;
import com.example.bulletinboard.exception.ReportOperationException;
import com.example.bulletinboard.exception.ReportOperationException.Reason;
import com.example.bulletinboard.exception.handler.GlobalExceptionHandler;
import com.example.bulletinboard.model.ReportReason;
import com.example.bulletinboard.repository.UserRepository;
import com.example.bulletinboard.security.SecurityConfig;
import com.example.bulletinboard.service.ReportService;
import com.example.bulletinboard.service.ReportSubmissionService;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

@WebMvcTest(ReportApiController.class)
@Import(SecurityConfig.class)
class ReportApiControllerTest {
    private static final String PATH = "/api/topics/44/reports";
    private static final String ANSWER_PATH = "/api/answers/44/reports";
    private static final String EMAIL = "reporter@example.com";
    @Autowired MockMvc mvc;
    @MockitoBean ReportSubmissionService submission;
    @MockitoBean ReportService reports;
    @MockitoBean UserRepository users;

    @Test
    void successOnlyExposesFixedMessageAndUsesPrincipal() throws Exception {
        var result = mvc.perform(post(PATH).with(user(EMAIL)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"ABUSE\",\"detail\":\"説明\",\"ownerId\":999}"))
                .andExpect(status().isCreated()).andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("通報を受け付けました。");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("reporter", "ownerId", "説明", "999");
        verify(submission).submitTopic(eq(44L), eq(EMAIL), eq(new ReportRequest(ReportReason.ABUSE, "説明")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"reason\":\"UNKNOWN\"}", "{\"reason\":1}"})
    void invalidOrNumericReasonReturns400(String json) throws Exception {
        mvc.perform(post(PATH).with(user(EMAIL)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json)).andExpect(status().isBadRequest());
        verifyNoInteractions(submission);
    }

    @Test
    void detailAbove500Returns400WithoutSaving() throws Exception {
        mvc.perform(post(PATH).with(user(EMAIL)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"SPAM\",\"detail\":\"" + "a".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.detail").exists());
        verifyNoInteractions(submission);
    }

    @Test
    void blankOtherIsRejectedWithFixed400() throws Exception {
        doThrow(new ReportOperationException(Reason.INVALID, 44L)).when(submission)
                .submitTopic(eq(44L), eq(EMAIL), any());
        mvc.perform(post(PATH).with(user(EMAIL)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"OTHER\",\"detail\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Reason.INVALID.message));
    }

    @Test
    void unauthenticatedCannotSubmit() throws Exception {
        mvc.perform(post(PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"SPAM\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(submission);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingOrInvalidCsrfCannotSubmit(boolean invalid) throws Exception {
        var request = post(PATH).with(user(EMAIL)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"SPAM\"}");
        if (invalid) request.with(csrf().useInvalidToken());
        mvc.perform(request).andExpect(status().isForbidden());
        verifyNoInteractions(submission);
    }

    @Test
    void transactionFailureHasSafe500AndSingleLogWithoutThrowable() throws Exception {
        String secret = "SELECT fake_password FROM reports WHERE email='secret@example.com'";
        doThrow(new TransactionSystemException(secret)).when(submission).submitTopic(eq(44L), eq(EMAIL), any());
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var response = mvc.perform(post(PATH).with(user(EMAIL)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"SPAM\"}"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.error").value("Internal Server Error"))
                    .andExpect(jsonPath("$.message").value(Reason.FAILED.message))
                    .andExpect(jsonPath("$.path").value(PATH)).andReturn();
            assertThat(response.getResponse().getContentAsString())
                    .doesNotContain("SELECT", "fake_password", "secret@example.com");
            var errors = appender.list.stream().filter(e -> e.getLevel() == Level.ERROR).toList();
            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst().getFormattedMessage())
                    .isEqualTo("report_operation_error targetId=44 errorType=TransactionSystemException");
            assertThat(errors.getFirst().getThrowableProxy()).isNull();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void answerSuccessUsesPrincipalAndReturnsOnlyFixedMessage() throws Exception {
        var response = mvc.perform(post(ANSWER_PATH).with(user(EMAIL)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"PRIVACY\",\"detail\":\"private text\",\"reporterId\":999}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("通報を受け付けました。"))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("private text", "reporterId", "999", EMAIL);
        verify(submission).submitAnswer(44L, EMAIL, new ReportRequest(ReportReason.PRIVACY, "private text"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"reason\":\"UNKNOWN\"}", "{\"reason\":2}"})
    void answerInvalidReasonReturns400(String json) throws Exception {
        mvc.perform(post(ANSWER_PATH).with(user(EMAIL)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json)).andExpect(status().isBadRequest());
        verifyNoInteractions(submission);
    }

    @Test
    void answerUnauthenticatedRequestIsRejected() throws Exception {
        mvc.perform(post(ANSWER_PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"SPAM\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(submission);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void answerMissingOrInvalidCsrfIsRejected(boolean invalid) throws Exception {
        var request = post(ANSWER_PATH).with(user(EMAIL)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"SPAM\"}");
        if (invalid) request.with(csrf().useInvalidToken());
        mvc.perform(request).andExpect(status().isForbidden());
        verifyNoInteractions(submission);
    }

    @Test
    void answerDatabaseFailureUsesSafeFixedResponseAndLog() throws Exception {
        String secret = "SELECT fake_password WHERE email='secret@example.com'";
        doThrow(new TransactionSystemException(secret)).when(submission).submitAnswer(eq(44L), eq(EMAIL), any());
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var response = mvc.perform(post(ANSWER_PATH).with(user(EMAIL)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"SPAM\"}"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value(Reason.FAILED.message))
                    .andExpect(jsonPath("$.path").value(ANSWER_PATH)).andReturn();
            assertThat(response.getResponse().getContentAsString()).doesNotContain("SELECT", "secret@example.com");
            var errors = appender.list.stream().filter(e -> e.getLevel() == Level.ERROR).toList();
            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst().getFormattedMessage())
                    .isEqualTo("report_operation_error targetId=44 errorType=TransactionSystemException");
            assertThat(errors.getFirst().getThrowableProxy()).isNull();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
