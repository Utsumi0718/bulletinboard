package com.example.bulletinboard.exception.handler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import com.example.bulletinboard.dto.error.ErrorResponse;
import com.example.bulletinboard.exception.TopicNotFoundException;


/*
 * 【クラスの役割】
 * GlobalExceptionHandlerの例外ハンドリング処理を
 * 単体で検証するテストクラスです。
 *
 * 各Exceptionが発生した場合に、
 * GlobalExceptionHandlerによって
 *
 * - 想定したHTTP Statusへ変換されること
 * - ErrorResponseまたはValidationErrorResponseが返されること
 * - status / error / message / path が
 *   正しく設定されること
 *
 * を確認します。
 *
 * Controllerを経由するController Testとは分けて、
 * GlobalExceptionHandler自体の責務を直接検証します。
 *
 * 【今後の主な検証対象】
 * - IllegalArgumentException
 *   → 400 Bad Request
 *   → ErrorResponse
 *
 * - MethodArgumentNotValidException
 *   → 400 Bad Request
 *   → ValidationErrorResponse
 *
 * - TopicNotFoundException
 *   → 404 Not Found
 *   → ErrorResponse
 *
 * - ForbiddenOperationException
 *   → 403 Forbidden
 *   → ErrorResponse
 *
 * - TopicEditConflictException
 *   → 409 Conflict
 *   → ErrorResponse
 *
 * - UserNotFoundException
 *   → 500 Internal Server Error
 *   → ErrorResponse
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler();

    @Test
    void handleTopicNotFound_ShouldReturnNotFound() {

        TopicNotFoundException exception =
                new TopicNotFoundException(
                        "このお題は存在しないか、削除されています。"
                );

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.setRequestURI("/api/topics/999");

        ResponseEntity<ErrorResponse> response =
                handler.handleTopicNotFound(
                        exception,
                        request
                );

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(response.getBody())
                .isNotNull();

        assertThat(response.getBody().status())
                .isEqualTo(404);

        assertThat(response.getBody().error())
                .isEqualTo("Not Found");

        assertThat(response.getBody().message())
                .isEqualTo(
                        "このお題は存在しないか、削除されています。"
                );

        assertThat(response.getBody().path())
                .isEqualTo("/api/topics/999");
    }
}