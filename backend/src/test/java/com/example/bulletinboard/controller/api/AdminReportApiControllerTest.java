package com.example.bulletinboard.controller.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.UnexpectedRollbackException;

import com.example.bulletinboard.dto.report.AdminReportDetailResponse;
import com.example.bulletinboard.dto.report.AdminReportListResponse;
import com.example.bulletinboard.exception.ReportOperationException;
import com.example.bulletinboard.exception.handler.GlobalExceptionHandler;
import com.example.bulletinboard.model.ReportReason;
import com.example.bulletinboard.model.ReportStatus;
import com.example.bulletinboard.model.ReportTargetType;
import com.example.bulletinboard.repository.UserRepository;
import com.example.bulletinboard.security.SecurityConfig;
import com.example.bulletinboard.service.AdminReportService;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

@WebMvcTest(AdminReportApiController.class)
@Import(SecurityConfig.class)
class AdminReportApiControllerTest {
    private static final String ADMIN = "admin@example.com";
    private static final String LIST = "/api/admin/reports";
    private static final String DETAIL = "/api/admin/reports/44";
    private static final String STATUS = "/api/admin/reports/44/status";

    @Autowired MockMvc mvc;
    @MockitoBean AdminReportService service;
    @MockitoBean UserRepository users;

    @Test
    void listDefaultsAndLimitedFields() throws Exception {
        var item = new AdminReportListResponse(44L, ReportTargetType.TOPIC, 5L,
                8L, "reporter", 9L, "owner", ReportReason.SPAM,
                "スパム・迷惑行為", ReportStatus.UNHANDLED, LocalDateTime.now());
        when(service.list(0, 20, null, null, ADMIN))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1));
        String body = mvc.perform(get(LIST).with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].targetType").value("TOPIC"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("email", "detail", "password");
    }

    @Test
    void listPassesFiltersToService() throws Exception {
        when(service.list(2, 10, ReportStatus.RESOLVED, 7L, ADMIN))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 10), 0));
        mvc.perform(get(LIST).with(user(ADMIN).roles("ADMIN"))
                .param("page", "2").param("size", "10")
                .param("status", "RESOLVED").param("reporterUserId", "7"))
                .andExpect(status().isOk());
        verify(service).list(2, 10, ReportStatus.RESOLVED, 7L, ADMIN);
    }

    @Test
    void listAcceptsStatusWithoutReporter() throws Exception {
        when(service.list(0, 20, ReportStatus.IN_PROGRESS, null, ADMIN))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        mvc.perform(get(LIST).with(user(ADMIN).roles("ADMIN"))
                .param("status", "IN_PROGRESS")).andExpect(status().isOk());
        verify(service).list(0, 20, ReportStatus.IN_PROGRESS, null, ADMIN);
    }

    @Test
    void listAcceptsReporterWithoutStatus() throws Exception {
        when(service.list(0, 20, null, 7L, ADMIN))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        mvc.perform(get(LIST).with(user(ADMIN).roles("ADMIN"))
                .param("reporterUserId", "7")).andExpect(status().isOk());
        verify(service).list(0, 20, null, 7L, ADMIN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "UNKNOWN", "unhandled", "1"})
    void invalidStatusFilterIsRejectedBeforeService(String value) throws Exception {
        mvc.perform(get(LIST).with(user(ADMIN).roles("ADMIN")).param("status", value))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void detailReturnsPrivateFieldsOnlyToAdmin() throws Exception {
        when(service.detail(44L, ADMIN)).thenReturn(detailResponse());
        mvc.perform(get(DETAIL).with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reporter.email").value("reporter@example.com"))
                .andExpect(jsonPath("$.reporterReportCount").value(3));
    }

    @Test
    void missingReportReturns404() throws Exception {
        when(service.detail(44L, ADMIN)).thenThrow(
                new ReportOperationException(ReportOperationException.Reason.NOT_FOUND, 44L));
        mvc.perform(get(DETAIL).with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.path").value(DETAIL));
    }

    @Test
    void patchPassesStatusAndPrincipal() throws Exception {
        when(service.updateStatus(44L, ReportStatus.RESOLVED, ADMIN)).thenReturn(detailResponse());
        mvc.perform(patch(STATUS).with(user(ADMIN).roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk());
        verify(service).updateStatus(44L, ReportStatus.RESOLVED, ADMIN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"status\":null}", "{\"status\":1}",
            "{\"status\":\"\"}", "{\"status\":\"UNKNOWN\"}",
            "{\"status\":\"resolved\"}"})
    void invalidPatchStatusReturns400WithoutService(String body) throws Exception {
        mvc.perform(patch(STATUS).with(user(ADMIN).roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void anonymousAndRegularUserCannotReadPrivateDetail() throws Exception {
        mvc.perform(get(DETAIL)).andExpect(status().isUnauthorized());
        mvc.perform(get(DETAIL).with(user("member@example.com"))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void patchRequiresAdminAndCsrf() throws Exception {
        String body = "{\"status\":\"RESOLVED\"}";
        mvc.perform(patch(STATUS).with(user(ADMIN).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(patch(STATUS).with(user("member@example.com")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(patch(STATUS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({
            "LIST,DATA", "LIST,TRANSACTION", "LIST,ROLLBACK",
            "DETAIL,DATA", "DETAIL,TRANSACTION", "DETAIL,ROLLBACK",
            "STATUS_CHANGE,DATA", "STATUS_CHANGE,TRANSACTION", "STATUS_CHANGE,ROLLBACK"
    })
    void databaseFailureHasFixed500AndSafeSingleLog(String operation, String failure) throws Exception {
        String secret = "SELECT fake_password FROM reports WHERE email='secret@example.com'";
        RuntimeException ex = switch (failure) {
            case "DATA" -> new DataAccessResourceFailureException(secret);
            case "TRANSACTION" -> new TransactionSystemException(secret);
            default -> new UnexpectedRollbackException(secret);
        };
        String path;
        if (operation.equals("LIST")) {
            path = LIST;
            when(service.list(0, 20, null, null, ADMIN)).thenThrow(ex);
        } else if (operation.equals("DETAIL")) {
            path = DETAIL;
            when(service.detail(44L, ADMIN)).thenThrow(ex);
        } else {
            path = STATUS;
            when(service.updateStatus(44L, ReportStatus.RESOLVED, ADMIN)).thenThrow(ex);
        }
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var request = operation.equals("LIST") ? get(path).with(user(ADMIN).roles("ADMIN")) :
                    operation.equals("DETAIL") ? get(path).with(user(ADMIN).roles("ADMIN")) :
                    patch(path).with(user(ADMIN).roles("ADMIN")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\"}");
            String body = mvc.perform(request)
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.error").value("Internal Server Error"))
                    .andExpect(jsonPath("$.path").value(path))
                    .andExpect(jsonPath("$.message").value(
                            "通報の管理処理でエラーが発生しました。画面を再読み込みして状態を確認してください。"))
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain("SELECT", "fake_password", "secret@example.com");
            var errors = appender.list.stream().filter(event -> event.getLevel() == Level.ERROR).toList();
            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst().getFormattedMessage())
                    .isEqualTo("admin_report_operation_error operation=" + operation + " reportId="
                            + (operation.equals("LIST") ? "null" : "44")
                            + " errorType=" + ex.getClass().getSimpleName());
            assertThat(errors.getFirst().getThrowableProxy()).isNull();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private AdminReportDetailResponse detailResponse() {
        return new AdminReportDetailResponse(44L, ReportTargetType.TOPIC, 5L,
                new com.example.bulletinboard.dto.report.AdminReportUserResponse(8L, "reporter",
                        "reporter@example.com", com.example.bulletinboard.model.AccountStatus.ACTIVE),
                9L, null, ReportReason.SPAM, "スパム・迷惑行為", "detail",
                ReportStatus.RESOLVED, LocalDateTime.now(), LocalDateTime.now(), 3,
                "PUBLIC", null, List.of());
    }
}
