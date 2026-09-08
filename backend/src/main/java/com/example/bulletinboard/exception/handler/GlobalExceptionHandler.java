package com.example.bulletinboard.exception.handler;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.bulletinboard.dto.error.ErrorResponse;
import com.example.bulletinboard.dto.error.ValidationErrorResponse;
import com.example.bulletinboard.exception.AnswerNotFoundException;
import com.example.bulletinboard.exception.ForbiddenOperationException;
import com.example.bulletinboard.exception.TopicEditConflictException;
import com.example.bulletinboard.exception.TopicNotFoundException;
import com.example.bulletinboard.exception.UserNotFoundException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 【クラスの役割】
 * REST APIで発生した例外を共通で受け取り、
 * HTTP StatusとエラーレスポンスDTOへ変換して
 * フロントエンドへJSON形式で返すクラスです。
 *
 * 各REST Controllerで個別にtry-catchを書くのではなく、
 * API全体の例外処理をこのクラスへ集約します。
 *
 * 【現在の対応内容】
 * - TopicNotFoundException
 *   → 404 Not Found
 *   → ErrorResponseを返却
 *
 * - IllegalArgumentException
 *   → 400 Bad Request
 *   → ErrorResponseを返却
 *
 * - UserNotFoundException
 *   → 500 Internal Server Error
 *   → ErrorResponseを返却
 *
 * - MethodArgumentNotValidException
 *   → 400 Bad Request
 *   → ValidationErrorResponseを返却
 *
 * - ForbiddenOperationException
 *   → 403 Forbidden
 *   → ErrorResponseを返却
 *
 * - TopicEditConflictException
 *   → 409 Conflict
 *   → ErrorResponseを返却
 *
 * - AnswerNotFoundException
 *   → 404 Not Found
 *   → ErrorResponseを返却
 *
 * 【今後の拡張予定】
 * - 必要に応じて他の業務例外も追加
 *
 * ※ 旧Thymeleaf ControllerのFlashMessage処理とは分離し、
 *    REST API専用の例外処理として使用します。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Topicが存在しない、または論理削除済みの場合の
     * TopicNotFoundExceptionを処理します。
     *
     * @param ex      発生したTopicNotFoundException
     * @param request エラーが発生したHTTPリクエスト
     * @return 404 Not FoundとErrorResponse
     */
    @ExceptionHandler(TopicNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTopicNotFound(
            TopicNotFoundException ex,
            HttpServletRequest request) {

        ErrorResponse response = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(response);
    }

    /**
      * 不正なリクエスト値などによって発生した
      * IllegalArgumentExceptionを処理します。
      *
      * 現在は主にTopic一覧検索で
      * keywordが空文字・空白だった場合に使用します。
      *
      * @param ex      発生したIllegalArgumentException
      * @param request エラーが発生したHTTPリクエスト
      * @return 400 Bad RequestとErrorResponse
      */
      @ExceptionHandler(IllegalArgumentException.class)
      public ResponseEntity<ErrorResponse> handleIllegalArgument(
        IllegalArgumentException ex,
        HttpServletRequest request) {

           ErrorResponse response = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
    );

         return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(response);
      }

      /**
        * 認証情報に対応するUserを取得できなかった場合の
        * UserNotFoundExceptionを処理します。
        *
        * @param ex      発生したUserNotFoundException
        * @param request エラーが発生したHTTPリクエスト
        * @return 500 Internal Server ErrorとErrorResponse
        */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(
        UserNotFoundException ex,
        HttpServletRequest request) {

    ErrorResponse response = new ErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
    );

    return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(response);
}

/**
 * @ValidによるリクエストボディのValidationに失敗した場合の
 * MethodArgumentNotValidExceptionを処理します。
 *
 * フィールドごとのValidationエラーをMapへ変換し、
 * 400 Bad RequestとValidationErrorResponseを返します。
 *
 * @param ex      発生したMethodArgumentNotValidException
 * @param request エラーが発生したHTTPリクエスト
 * @return 400 Bad RequestとValidationErrorResponse
 */
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ValidationErrorResponse> handleValidationException(
        MethodArgumentNotValidException ex,
        HttpServletRequest request) {

      Map<String, String> fieldErrors = new LinkedHashMap<>();

    ex.getBindingResult()
            .getFieldErrors()
            .forEach(fieldError ->
                    fieldErrors.putIfAbsent(
                            fieldError.getField(),
                            fieldError.getDefaultMessage()
                    )
            );

    ValidationErrorResponse response =
            new ValidationErrorResponse(
                    HttpStatus.BAD_REQUEST.value(),
                    HttpStatus.BAD_REQUEST.getReasonPhrase(),
                    "入力内容に誤りがあります。",
                    request.getRequestURI(),
                    fieldErrors
            );

    return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(response);
}

/**
 * 認証済みユーザーが権限を持たない操作を行おうとした場合の
 * ForbiddenOperationExceptionを処理します。
 *
 * @param ex      発生したForbiddenOperationException
 * @param request エラーが発生したHTTPリクエスト
 * @return 403 ForbiddenとErrorResponse
 */
@ExceptionHandler(ForbiddenOperationException.class)
public ResponseEntity<ErrorResponse> handleForbiddenOperation(
        ForbiddenOperationException ex,
        HttpServletRequest request) {

    ErrorResponse response = new ErrorResponse(
            HttpStatus.FORBIDDEN.value(),
            HttpStatus.FORBIDDEN.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
    );

    return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(response);
}

/**
 * Topic編集時に業務ルール上の競合が発生した場合の
 * TopicEditConflictExceptionを処理します。
 *
 * @param ex      発生したTopicEditConflictException
 * @param request エラーが発生したHTTPリクエスト
 * @return 409 ConflictとErrorResponse
 */
@ExceptionHandler(TopicEditConflictException.class)
public ResponseEntity<ErrorResponse> handleTopicEditConflict(
        TopicEditConflictException ex,
        HttpServletRequest request) {

    ErrorResponse response = new ErrorResponse(
            HttpStatus.CONFLICT.value(),
            HttpStatus.CONFLICT.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
    );

    return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(response);
}

/**
 * Answerが存在しない、または論理削除済みの場合の
 * AnswerNotFoundExceptionを処理します。
 *
 * @param ex      発生したAnswerNotFoundException
 * @param request エラーが発生したHTTPリクエスト
 * @return 404 Not FoundとErrorResponse
 */
@ExceptionHandler(AnswerNotFoundException.class)
public ResponseEntity<ErrorResponse> handleAnswerNotFound(
        AnswerNotFoundException ex,
        HttpServletRequest request) {

    ErrorResponse response = new ErrorResponse(
            HttpStatus.NOT_FOUND.value(),
            HttpStatus.NOT_FOUND.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
    );

    return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(response);
}


}